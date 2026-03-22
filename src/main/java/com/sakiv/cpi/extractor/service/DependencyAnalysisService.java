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
                    String address = normalizeAddress(resolveAddress(adapter));
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
                    String address = normalizeAddress(resolveAddress(adapter));
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

                String queueName = resolveJmsQueue(adapter);
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
