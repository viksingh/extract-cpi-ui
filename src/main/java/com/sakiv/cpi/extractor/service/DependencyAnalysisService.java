package com.sakiv.cpi.extractor.service;

import com.sakiv.cpi.extractor.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * Analyzes dependencies between iFlows from an ExtractionResult.
 * Runs 6 analysis passes on in-memory data — no additional API calls needed.
 */
public class DependencyAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(DependencyAnalysisService.class);

    /**
     * Analyze dependencies from a completed extraction result.
     */
    public DependencyGraph analyze(ExtractionResult result, Consumer<String> progressCallback) {
        DependencyGraph graph = new DependencyGraph();

        List<IntegrationFlow> allFlows = result.getAllFlows();
        progress(progressCallback, String.format("Analyzing dependencies across %d flows...", allFlows.size()));

        Map<String, IFlowContent> contentMap = new LinkedHashMap<>();
        for (IntegrationFlow flow : allFlows) {
            graph.addFlow(flow.getId(), flow);
            if (flow.getIflowContent() != null) {
                contentMap.put(flow.getId(), flow.getIflowContent());
            }
        }
        progress(progressCallback, String.format("%d flows have parsed bundle content.", contentMap.size()));

        if (contentMap.isEmpty()) {
            progress(progressCallback, "No bundle content available — skipping dependency analysis. Enable deep extraction.");
            return graph;
        }

        progress(progressCallback, "Analyzing ProcessDirect dependencies...");
        analyzeProcessDirect(allFlows, contentMap, graph);

        progress(progressCallback, "Analyzing JMS queue dependencies...");
        analyzeJmsQueues(allFlows, contentMap, graph);

        progress(progressCallback, String.format("Dependency analysis complete: %d dependencies, %d unresolved.",
                graph.getDependencies().size(), graph.getUnresolvedReferences().size()));

        return graph;
    }

    // =========================================================================
    // ProcessDirect Analysis
    // =========================================================================

    private void analyzeProcessDirect(List<IntegrationFlow> allFlows,
                                       Map<String, IFlowContent> contentMap,
                                       DependencyGraph graph) {
        Map<String, String> senderAddresses = new HashMap<>();
        Map<String, String> senderFlowNames = new HashMap<>();

        for (IntegrationFlow flow : allFlows) {
            IFlowContent content = contentMap.get(flow.getId());
            if (content == null) continue;

            for (IFlowAdapter adapter : content.getAdapters()) {
                if ("ProcessDirect".equalsIgnoreCase(adapter.getAdapterType())
                        && "sender".equalsIgnoreCase(adapter.getDirection())) {
                    String address = normalizeAddress(resolveExternalizedParams(
                            resolveAddress(adapter), flow.getConfigurations()));
                    if (address != null) {
                        senderAddresses.put(address, flow.getId());
                        senderFlowNames.put(address, flow.getName());
                    }
                }
            }
        }

        for (IntegrationFlow flow : allFlows) {
            IFlowContent content = contentMap.get(flow.getId());
            if (content == null) continue;

            for (IFlowAdapter adapter : content.getAdapters()) {
                if ("ProcessDirect".equalsIgnoreCase(adapter.getAdapterType())
                        && "receiver".equalsIgnoreCase(adapter.getDirection())) {
                    String address = normalizeAddress(resolveExternalizedParams(
                            resolveAddress(adapter), flow.getConfigurations()));
                    if (address == null) continue;

                    String targetFlowId = senderAddresses.get(address);
                    if (targetFlowId != null && !targetFlowId.equals(flow.getId())) {
                        IntegrationFlow targetFlow = findFlow(allFlows, targetFlowId);
                        graph.addDependency(new Dependency(
                                flow.getId(), flow.getName(), flow.getPackageId(),
                                targetFlowId,
                                targetFlow != null ? targetFlow.getName() : targetFlowId,
                                targetFlow != null ? targetFlow.getPackageId() : null,
                                DependencyType.PROCESS_DIRECT,
                                "Address: " + address));
                    } else if (targetFlowId == null) {
                        graph.addUnresolvedReference(
                                "ProcessDirect address '" + address + "' from " + flow.getName() +
                                " has no matching sender");
                    }
                }
            }
        }
    }

    // =========================================================================
    // JMS Queue Analysis
    // =========================================================================

    private void analyzeJmsQueues(List<IntegrationFlow> allFlows,
                                   Map<String, IFlowContent> contentMap,
                                   DependencyGraph graph) {
        // Producers: flows with JMS Receiver adapter (sends to queue)
        Map<String, List<IntegrationFlow>> queueProducers = new HashMap<>();
        // Consumers: flows with JMS Sender adapter (reads from queue)
        Map<String, List<IntegrationFlow>> queueConsumers = new HashMap<>();

        for (IntegrationFlow flow : allFlows) {
            IFlowContent content = contentMap.get(flow.getId());
            if (content == null) continue;

            for (IFlowAdapter adapter : content.getAdapters()) {
                String type = adapter.getAdapterType();
                if (type == null || !type.toLowerCase().contains("jms")) continue;

                String queueName = resolveExternalizedParams(
                        resolveJmsQueue(adapter), flow.getConfigurations());
                if (queueName == null) continue;

                if ("receiver".equalsIgnoreCase(adapter.getDirection())) {
                    queueProducers.computeIfAbsent(queueName, k -> new ArrayList<>()).add(flow);
                } else if ("sender".equalsIgnoreCase(adapter.getDirection())) {
                    queueConsumers.computeIfAbsent(queueName, k -> new ArrayList<>()).add(flow);
                }
            }
        }

        // Create dependencies: producer → consumer for each shared queue
        for (Map.Entry<String, List<IntegrationFlow>> entry : queueProducers.entrySet()) {
            String queue = entry.getKey();
            List<IntegrationFlow> producers = entry.getValue();
            List<IntegrationFlow> consumers = queueConsumers.getOrDefault(queue, List.of());

            for (IntegrationFlow producer : producers) {
                for (IntegrationFlow consumer : consumers) {
                    if (!producer.getId().equals(consumer.getId())) {
                        graph.addDependency(new Dependency(
                                producer.getId(), producer.getName(), producer.getPackageId(),
                                consumer.getId(), consumer.getName(), consumer.getPackageId(),
                                DependencyType.JMS_QUEUE,
                                "Queue: " + queue));
                    }
                }
            }

            // Unresolved: producers with no consumers
            if (consumers.isEmpty()) {
                for (IntegrationFlow producer : producers) {
                    graph.addUnresolvedReference(
                            "JMS queue '" + queue + "' produced by " + producer.getName() +
                            " has no consumer");
                }
            }
        }

        // Unresolved: consumers with no producers
        for (Map.Entry<String, List<IntegrationFlow>> entry : queueConsumers.entrySet()) {
            if (!queueProducers.containsKey(entry.getKey())) {
                for (IntegrationFlow consumer : entry.getValue()) {
                    graph.addUnresolvedReference(
                            "JMS queue '" + entry.getKey() + "' consumed by " + consumer.getName() +
                            " has no producer");
                }
            }
        }
    }

    private String resolveJmsQueue(IFlowAdapter adapter) {
        Map<String, String> props = adapter.getProperties();
        if (props == null) return null;

        String dir = adapter.getDirection() != null ? adapter.getDirection() : "";
        // Try direction-specific key first
        String preferredKey = "Receiver".equalsIgnoreCase(dir) ? "QueueName_outbound" : "QueueName_inbound";
        String val = props.get(preferredKey);
        if (val != null && !val.isBlank()) return val.trim();

        for (String key : List.of("QueueName_outbound", "QueueName_inbound",
                "Destination", "QueueName", "destination", "queueName")) {
            val = props.get(key);
            if (val != null && !val.isBlank()) return val.trim();
        }
        return null;
    }

    // =========================================================================
    // Unique Interface Tracing
    // =========================================================================

    /**
     * Trace end-to-end unique interfaces by following PD/JMS chains transitively.
     * An entry flow has an external sender adapter; an exit flow has an external receiver adapter.
     * Returns a list of traced paths, each representing one logical interface.
     */
    public List<UniqueInterfacePath> traceUniqueInterfaces(DependencyGraph graph) {
        List<UniqueInterfacePath> results = new ArrayList<>();
        Map<String, IntegrationFlow> flowsById = graph.getFlowsById();

        // Identify flows that are targets of internal links
        Set<String> internalTargets = new HashSet<>();
        for (Dependency dep : graph.getDependencies()) {
            internalTargets.add(dep.getTargetFlowId());
        }

        for (IntegrationFlow flow : flowsById.values()) {
            IFlowContent content = flow.getIflowContent();
            if (content == null) continue;

            // Find external sender adapters on this flow
            for (IFlowAdapter adapter : content.getAdapters()) {
                if (!"sender".equalsIgnoreCase(adapter.getDirection())) continue;
                if (isInternalAdapterType(adapter.getAdapterType())) continue;

                // This flow has an external sender — it's an entry point
                String senderType = adapter.getAdapterType() != null ? adapter.getAdapterType() : "Unknown";
                String senderAddress = adapter.getAddress() != null ? adapter.getAddress() : "";

                // DFS to find all end-to-end paths
                List<IntegrationFlow> path = new ArrayList<>();
                path.add(flow);
                Set<String> visited = new HashSet<>();
                visited.add(flow.getId());
                traceChainDFS(flow, path, visited, senderType, senderAddress,
                        graph, flowsById, results);
            }
        }

        // Also add standalone flows (external sender + external receiver, no internal links)
        // These are already covered by the DFS above (outgoing is empty → terminates)

        // Filter out single-flow paths (no PD/JMS chaining) and deduplicate
        Map<String, UniqueInterfacePath> deduped = new LinkedHashMap<>();
        for (UniqueInterfacePath uip : results) {
            if (uip.getChainLength() <= 1) continue; // skip standalone flows
            String key = uip.getPathKey();
            if (!deduped.containsKey(key)) {
                deduped.put(key, uip);
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private void traceChainDFS(IntegrationFlow current, List<IntegrationFlow> path,
                                Set<String> visited, String senderType, String senderAddress,
                                DependencyGraph graph, Map<String, IntegrationFlow> flowsById,
                                List<UniqueInterfacePath> results) {
        List<Dependency> outgoing = graph.getOutgoingDependencies(current.getId());

        // Check if current flow has external receiver adapters
        List<String[]> externalReceivers = getExternalReceiverAdapters(current);

        if (outgoing.isEmpty()) {
            // Terminal flow — emit for each external receiver, or as dead end
            if (!externalReceivers.isEmpty()) {
                for (String[] recv : externalReceivers) {
                    results.add(new UniqueInterfacePath(
                            new ArrayList<>(path), senderType, senderAddress,
                            recv[0], recv[1], false));
                }
            } else {
                results.add(new UniqueInterfacePath(
                        new ArrayList<>(path), senderType, senderAddress,
                        "(dead end)", "", false));
            }
            return;
        }

        // If flow has external receivers AND outgoing internal links, emit here too
        if (!externalReceivers.isEmpty()) {
            for (String[] recv : externalReceivers) {
                results.add(new UniqueInterfacePath(
                        new ArrayList<>(path), senderType, senderAddress,
                        recv[0], recv[1], false));
            }
        }

        // Continue traversal through internal links
        for (Dependency dep : outgoing) {
            String targetId = dep.getTargetFlowId();
            if (visited.contains(targetId)) {
                // Cycle detected
                List<IntegrationFlow> cyclePath = new ArrayList<>(path);
                IntegrationFlow targetFlow = flowsById.get(targetId);
                if (targetFlow != null) cyclePath.add(targetFlow);
                results.add(new UniqueInterfacePath(
                        cyclePath, senderType, senderAddress,
                        "(cycle)", "", true));
                continue;
            }

            IntegrationFlow targetFlow = flowsById.get(targetId);
            if (targetFlow == null) continue;

            path.add(targetFlow);
            visited.add(targetId);
            traceChainDFS(targetFlow, path, visited, senderType, senderAddress,
                    graph, flowsById, results);
            visited.remove(targetId);
            path.remove(path.size() - 1);
        }
    }

    private List<String[]> getExternalReceiverAdapters(IntegrationFlow flow) {
        List<String[]> receivers = new ArrayList<>();
        IFlowContent content = flow.getIflowContent();
        if (content == null) return receivers;

        for (IFlowAdapter adapter : content.getAdapters()) {
            if (!"receiver".equalsIgnoreCase(adapter.getDirection())) continue;
            if (isInternalAdapterType(adapter.getAdapterType())) continue;
            String type = adapter.getAdapterType() != null ? adapter.getAdapterType() : "Unknown";
            String address = adapter.getAddress() != null ? adapter.getAddress() : "";
            receivers.add(new String[]{type, address});
        }
        return receivers;
    }

    private boolean isInternalAdapterType(String type) {
        if (type == null) return false;
        String lower = type.toLowerCase();
        return lower.contains("processdirect") || lower.contains("jms");
    }

    /**
     * Represents a traced end-to-end interface path.
     */
    public static class UniqueInterfacePath {
        private final List<IntegrationFlow> flows;
        private final String senderAdapterType;
        private final String senderAddress;
        private final String receiverAdapterType;
        private final String receiverAddress;
        private final boolean cyclic;

        public UniqueInterfacePath(List<IntegrationFlow> flows,
                                    String senderAdapterType, String senderAddress,
                                    String receiverAdapterType, String receiverAddress,
                                    boolean cyclic) {
            this.flows = flows;
            this.senderAdapterType = senderAdapterType;
            this.senderAddress = senderAddress;
            this.receiverAdapterType = receiverAdapterType;
            this.receiverAddress = receiverAddress;
            this.cyclic = cyclic;
        }

        public List<IntegrationFlow> getFlows() { return flows; }
        public IntegrationFlow getEntryFlow() { return flows.get(0); }
        public IntegrationFlow getExitFlow() { return flows.get(flows.size() - 1); }
        public String getSenderAdapterType() { return senderAdapterType; }
        public String getSenderAddress() { return senderAddress; }
        public String getReceiverAdapterType() { return receiverAdapterType; }
        public String getReceiverAddress() { return receiverAddress; }
        public boolean isCyclic() { return cyclic; }
        public int getChainLength() { return flows.size(); }

        public String getChainPath() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < flows.size(); i++) {
                if (i > 0) sb.append(" -> ");
                sb.append(flows.get(i).getName() != null ? flows.get(i).getName() : flows.get(i).getId());
            }
            return sb.toString();
        }

        public String getIntermediateFlows() {
            if (flows.size() <= 2) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < flows.size() - 1; i++) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(flows.get(i).getName() != null ? flows.get(i).getName() : flows.get(i).getId());
            }
            return sb.toString();
        }

        public String getPackagesInvolved() {
            Set<String> packages = new LinkedHashSet<>();
            for (IntegrationFlow f : flows) {
                if (f.getPackageId() != null) packages.add(f.getPackageId());
            }
            return String.join(", ", packages);
        }

        public String getPathKey() {
            StringBuilder sb = new StringBuilder();
            for (IntegrationFlow f : flows) {
                if (sb.length() > 0) sb.append("|");
                sb.append(f.getId());
            }
            sb.append(">>").append(senderAdapterType).append(">>").append(receiverAdapterType);
            return sb.toString();
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Resolve the address from the adapter field, falling back to properties map
     * (handles case variations: "Address", "address", "EndpointAddress").
     */
    private String resolveAddress(IFlowAdapter adapter) {
        String address = adapter.getAddress();
        if (address != null && !address.isBlank()) return address;

        Map<String, String> props = adapter.getProperties();
        if (props == null) return null;

        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("address")
                    || entry.getKey().equalsIgnoreCase("EndpointAddress")) {
                String val = entry.getValue();
                if (val != null && !val.isBlank()) return val;
            }
        }
        return null;
    }

    private String resolveExternalizedParams(String value, List<Configuration> configs) {
        if (value == null || !value.contains("{{") || configs == null) return value;

        Map<String, String> configMap = new HashMap<>();
        for (Configuration cfg : configs) {
            if (cfg.getParameterKey() != null && cfg.getParameterValue() != null) {
                configMap.put(cfg.getParameterKey(), cfg.getParameterValue());
            }
        }

        String resolved = value;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{(.+?)\\}\\}").matcher(value);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String paramValue = configMap.get(paramName);
            if (paramValue != null && !paramValue.isBlank()) {
                resolved = resolved.replace("{{" + paramName + "}}", paramValue);
            }
        }
        return resolved;
    }

    private String normalizeAddress(String address) {
        if (address == null || address.isBlank()) return null;
        String normalized = address.trim().toLowerCase();
        if (!normalized.startsWith("/")) normalized = "/" + normalized;
        return normalized;
    }

    private IntegrationFlow findFlow(List<IntegrationFlow> flows, String flowId) {
        for (IntegrationFlow f : flows) {
            if (flowId.equals(f.getId())) return f;
        }
        return null;
    }

    private void progress(Consumer<String> callback, String message) {
        log.info(message);
        if (callback != null) callback.accept(message);
    }
}
