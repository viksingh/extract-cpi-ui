package com.sakiv.cpi.extractor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sakiv.cpi.extractor.config.CpiConfiguration;
import com.sakiv.cpi.extractor.model.*;
import com.sakiv.cpi.extractor.parser.IFlowBundleParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service layer for interacting with SAP CPI OData APIs.
 * Extracts Integration Packages, Flows, Value Mappings, Configurations,
 * and Runtime status information.
 */
public class CpiApiService {

    private static final Logger log = LoggerFactory.getLogger(CpiApiService.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    private final CpiConfiguration config;
    private final CpiHttpClient httpClient;
    private final IFlowBundleParser bundleParser = new IFlowBundleParser();

    // @author Vikas Singh | Created: 2025-11-15
    public CpiApiService(CpiConfiguration config, CpiHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    // =========================================================================
    // Integration Packages
    // =========================================================================

    /**
     * Fetch all Integration Packages from the tenant.
     */
    // @author Vikas Singh | Created: 2025-11-15
    public List<IntegrationPackage> getIntegrationPackages() throws IOException {
        log.info("Fetching Integration Packages...");
        String endpoint = config.get("cpi.api.packages", "/api/v1/IntegrationPackages");
        List<IntegrationPackage> packages = fetchAll(endpoint, IntegrationPackage.class);
        log.info("Found {} Integration Packages", packages.size());
        return packages;
    }

    /**
     * Fetch artifacts belonging to a specific package.
     */
    // @author Vikas Singh | Created: 2025-11-16
    public List<IntegrationFlow> getPackageFlows(String packageId) throws IOException {
        log.debug("Fetching flows for package: {}", packageId);
        String endpoint = String.format(
                "/api/v1/IntegrationPackages('%s')/IntegrationDesigntimeArtifacts", packageId);
        return fetchAll(endpoint, IntegrationFlow.class);
    }

    // @author Vikas Singh | Created: 2025-11-16
    public List<ValueMapping> getPackageValueMappings(String packageId) throws IOException {
        log.debug("Fetching value mappings for package: {}", packageId);
        String endpoint = String.format(
                "/api/v1/IntegrationPackages('%s')/ValueMappingDesigntimeArtifacts", packageId);
        return fetchAll(endpoint, ValueMapping.class);
    }

    // =========================================================================
    // Design-time Artifacts
    // =========================================================================

    /**
     * Fetch all Integration Design-time Artifacts (iFlows).
     */
    // @author Vikas Singh | Created: 2025-11-16
    public List<IntegrationFlow> getAllIntegrationFlows() throws IOException {
        log.info("Fetching all Integration Design-time Artifacts...");
        String endpoint = config.get("cpi.api.flows", "/api/v1/IntegrationDesigntimeArtifacts");
        List<IntegrationFlow> flows = fetchAll(endpoint, IntegrationFlow.class);
        log.info("Found {} Integration Flows", flows.size());
        return flows;
    }

    /**
     * Fetch all Value Mapping Design-time Artifacts.
     */
    // @author Vikas Singh | Created: 2025-11-16
    public List<ValueMapping> getAllValueMappings() throws IOException {
        log.info("Fetching all Value Mapping Artifacts...");
        String endpoint = config.get("cpi.api.valuemappings",
                "/api/v1/ValueMappingDesigntimeArtifacts");
        List<ValueMapping> mappings = fetchAll(endpoint, ValueMapping.class);
        log.info("Found {} Value Mappings", mappings.size());
        return mappings;
    }

    // =========================================================================
    // Configurations (Externalized Parameters)
    // =========================================================================

    /**
     * Fetch externalized configurations for a specific iFlow.
     */
    // @author Vikas Singh | Created: 2025-11-16
    public List<Configuration> getConfigurations(String artifactId, String version) throws IOException {
        log.debug("Fetching configurations for artifact: {} (version: {})", artifactId, version);
        String ver = (version == null || version.isBlank()) ? "active" : version;
        String endpoint = String.format(
                "/api/v1/IntegrationDesigntimeArtifacts(Id='%s',Version='%s')/Configurations",
                artifactId, ver);
        try {
            List<Configuration> configs = fetchAll(endpoint, Configuration.class);
            configs.forEach(c -> c.setArtifactId(artifactId));
            return configs;
        } catch (IOException e) {
            log.warn("Could not fetch configurations for {}: {}", artifactId, e.getMessage());
            return Collections.emptyList();
        }
    }

    // =========================================================================
    // Runtime Artifacts
    // =========================================================================

    /**
     * Fetch all deployed runtime artifacts with their status.
     */
    // @author Vikas Singh | Created: 2025-11-16
    public List<RuntimeArtifact> getRuntimeArtifacts() throws IOException {
        log.info("Fetching Runtime Artifacts (deployed status)...");
        String endpoint = config.get("cpi.api.runtime", "/api/v1/IntegrationRuntimeArtifacts");
        List<RuntimeArtifact> artifacts = fetchAll(endpoint, RuntimeArtifact.class);
        log.info("Found {} Runtime Artifacts", artifacts.size());
        return artifacts;
    }

    /**
     * Build a lookup map: artifact ID -> RuntimeArtifact for quick status resolution.
     */
    // @author Vikas Singh | Created: 2025-11-16
    public Map<String, RuntimeArtifact> getRuntimeStatusMap() throws IOException {
        return getRuntimeArtifacts().stream()
                .collect(Collectors.toMap(
                        RuntimeArtifact::getId,
                        r -> r,
                        (existing, replacement) -> replacement  // keep latest
                ));
    }

    // =========================================================================
    // Message Processing Logs
    // =========================================================================

    public List<MessageProcessingLog> getMessageProcessingLogs() throws IOException {
        log.info("Fetching Message Processing Logs...");
        String endpoint = config.get("cpi.api.messageProcessingLogs", "/api/v1/MessageProcessingLogs");
        List<MessageProcessingLog> logs = fetchAll(endpoint, MessageProcessingLog.class);
        log.info("Found {} Message Processing Log entries", logs.size());
        return logs;
    }

    /**
     * Fetch Message Processing Logs filtered by specific iFlow names.
     * Batches the $filter query to avoid URL length limits (~10 flows per batch).
     */
    public List<MessageProcessingLog> getMessageProcessingLogsForFlows(List<String> flowNames) throws IOException {
        if (flowNames == null || flowNames.isEmpty()) {
            return getMessageProcessingLogs();
        }

        log.info("Fetching Message Processing Logs for {} flows...", flowNames.size());
        String baseEndpoint = config.get("cpi.api.messageProcessingLogs", "/api/v1/MessageProcessingLogs");
        List<MessageProcessingLog> allLogs = new ArrayList<>();

        // Batch into groups of 10 to keep URL length manageable
        int batchSize = 10;
        for (int i = 0; i < flowNames.size(); i += batchSize) {
            List<String> batch = flowNames.subList(i, Math.min(i + batchSize, flowNames.size()));
            String filter = batch.stream()
                    .map(name -> "IntegrationFlowName eq '" + name.replace("'", "''") + "'")
                    .collect(Collectors.joining(" or "));
            String encodedFilter = URLEncoder.encode(filter, StandardCharsets.UTF_8);
            String endpoint = baseEndpoint + "?$filter=" + encodedFilter;
            log.info("Fetching MPL batch {}/{} ({} flows)", (i / batchSize) + 1,
                    (flowNames.size() + batchSize - 1) / batchSize, batch.size());
            try {
                List<MessageProcessingLog> batchLogs = fetchAll(endpoint, MessageProcessingLog.class);
                allLogs.addAll(batchLogs);
            } catch (IOException e) {
                log.warn("Failed to fetch MPL for batch starting at {}: {}", i, e.getMessage());
            }
        }

        log.info("Found {} Message Processing Log entries for selected flows", allLogs.size());
        return allLogs;
    }

    // =========================================================================
    // Full Extraction
    // =========================================================================

    /**
     * Perform a full extraction of all CPI artifacts based on configuration.
     */
    // @author Vikas Singh | Created: 2025-11-16
    public ExtractionResult extractAll() throws IOException {
        ExtractionResult result = new ExtractionResult(config.getBaseUrl());

        // 1. Fetch packages
        if (config.getBoolean("extract.packages", true)) {
            List<IntegrationPackage> packages = getIntegrationPackages();

            // Filter to selected package IDs if specified (from "Fetch Packages" pre-selection)
            String packageIdsCsv = config.get("extract.package.ids", "");
            if (packageIdsCsv != null && !packageIdsCsv.isBlank()) {
                Set<String> selectedIds = Arrays.stream(packageIdsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());
                int totalCount = packages.size();
                packages = packages.stream()
                        .filter(p -> selectedIds.contains(p.getId()))
                        .collect(Collectors.toList());
                log.info("Filtered packages by selection: {} -> {} (selected {} IDs)",
                        totalCount, packages.size(), selectedIds.size());
            }

            // Fetch artifacts per package
            for (IntegrationPackage pkg : packages) {
                try {
                    if (config.getBoolean("extract.flows", true)) {
                        List<IntegrationFlow> flows = getPackageFlows(pkg.getId());
                        pkg.setIntegrationFlows(flows);
                        result.getAllFlows().addAll(flows);
                    }
                    if (config.getBoolean("extract.valuemappings", true)) {
                        List<ValueMapping> vms = getPackageValueMappings(pkg.getId());
                        pkg.setValueMappings(vms);
                        result.getAllValueMappings().addAll(vms);
                    }
                } catch (IOException e) {
                    log.error("Error fetching artifacts for package {}: {}",
                            pkg.getId(), e.getMessage());
                }
            }
            result.setPackages(packages);
        }

        // 2. Fetch configurations for each flow
        if (config.getBoolean("extract.configurations", true)) {
            log.info("Fetching configurations for {} flows...", result.getAllFlows().size());
            for (IntegrationFlow flow : result.getAllFlows()) {
                try {
                    List<Configuration> configs = getConfigurations(flow.getId(), flow.getVersion());
                    flow.setConfigurations(configs);
                } catch (Exception e) {
                    log.warn("Skipping configurations for {}: {}", flow.getId(), e.getMessage());
                }
            }
        }

        // 3. Fetch runtime status and enrich flows
        if (config.getBoolean("extract.runtime.status", true)) {
            try {
                Map<String, RuntimeArtifact> runtimeMap = getRuntimeStatusMap();

                // Filter runtime artifacts to only those matching extracted flows
                Set<String> extractedFlowIds = result.getAllFlows().stream()
                        .map(IntegrationFlow::getId)
                        .collect(Collectors.toSet());
                List<RuntimeArtifact> filteredRuntime = runtimeMap.values().stream()
                        .filter(rt -> extractedFlowIds.contains(rt.getId()))
                        .collect(Collectors.toList());
                result.setRuntimeArtifacts(filteredRuntime);

                // Enrich flows with runtime info
                for (IntegrationFlow flow : result.getAllFlows()) {
                    RuntimeArtifact rt = runtimeMap.get(flow.getId());
                    if (rt != null) {
                        flow.setRuntimeStatus(rt.getStatus());
                        flow.setDeployedVersion(rt.getVersion());
                        flow.setDeployedBy(rt.getDeployedBy());
                        flow.setDeployedAt(rt.getDeployedOn());
                        flow.setRuntimeError(rt.getErrorInformation());
                    } else {
                        flow.setRuntimeStatus("NOT_DEPLOYED");
                    }
                }
            } catch (IOException e) {
                log.error("Error fetching runtime artifacts: {}", e.getMessage());
            }
        }

        // 4. Download and parse iFlow bundles
        if (config.getBoolean("extract.iflow.bundles", false)) {
            log.info("Downloading iFlow bundles for {} flows...", result.getAllFlows().size());
            int parsed = 0, failed = 0;
            for (IntegrationFlow flow : result.getAllFlows()) {
                try {
                    downloadAndParseBundle(flow);
                    parsed++;
                } catch (Exception e) {
                    log.warn("Failed to download/parse bundle for {}: {}", flow.getId(), e.getMessage());
                    flow.setBundleParseError(e.getMessage());
                    failed++;
                }
            }
            log.info("iFlow bundles: {} parsed, {} failed", parsed, failed);
        }

        // 5. Fetch message processing logs (filtered to extracted flows when available)
        if (config.getBoolean("extract.message.logs", false)) {
            try {
                List<String> flowNames = result.getAllFlows().stream()
                        .map(IntegrationFlow::getName)
                        .filter(n -> n != null && !n.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
                List<MessageProcessingLog> mplLogs = flowNames.isEmpty()
                        ? getMessageProcessingLogs()
                        : getMessageProcessingLogsForFlows(flowNames);
                result.setMessageProcessingLogs(mplLogs);
            } catch (IOException e) {
                log.error("Error fetching message processing logs: {}", e.getMessage());
            }
        }

        result.computeSummary();
        return result;
    }

    // =========================================================================
    // iFlow Bundle Download
    // =========================================================================

    /**
     * Download the ZIP bundle for an iFlow and parse its content.
     * Uses the $value endpoint: IntegrationDesigntimeArtifacts(Id='...', Version='...')/$value
     */
    // @author Vikas Singh | Created: 2026-02-22
    private void downloadAndParseBundle(IntegrationFlow flow) throws IOException {
        String ver = (flow.getVersion() == null || flow.getVersion().isBlank()) ? "active" : flow.getVersion();
        String endpoint = String.format(
                "/api/v1/IntegrationDesigntimeArtifacts(Id='%s',Version='%s')/$value",
                flow.getId(), ver);
        log.debug("Downloading bundle: {}", endpoint);
        byte[] zipBytes = httpClient.getBytes(endpoint);
        IFlowContent content = bundleParser.parse(zipBytes, flow.getId(), ver);
        flow.setIflowContent(content);
        flow.setBundleParsed(true);
    }

    // =========================================================================
    // Generic OData fetch with pagination
    // =========================================================================

    /**
     * Generic method to fetch all entities from an OData endpoint, handling
     * server-side pagination ($skiptoken / __next) and all known CPI
     * response formats.
     */
    // @author Vikas Singh | Created: 2025-11-22
    private <T> List<T> fetchAll(String endpoint, Class<T> entityClass) throws IOException {
        List<T> allResults = new ArrayList<>();
        String currentUrl = endpoint;

        // Add JSON format if not already present
        if (!currentUrl.contains("$format")) {
            currentUrl += (currentUrl.contains("?") ? "&" : "?") + "$format=json";
        }

        while (currentUrl != null) {
            String responseBody = httpClient.get(currentUrl);

            // Log first 1000 chars of response for debugging
            log.debug("Response for {} (first 1000 chars): {}",
                    endpoint, responseBody.substring(0, Math.min(responseBody.length(), 1000)));

            // Check if the response is XML instead of JSON (some tenants ignore Accept header)
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("<?xml") || trimmed.startsWith("<")) {
                log.error("Received XML response instead of JSON for {}. " +
                        "Ensure $format=json is in the URL.", endpoint);
                throw new IOException("API returned XML instead of JSON for: " + endpoint +
                        ". Check if your tenant requires a different API base URL.");
            }

            JsonNode root;
            try {
                root = mapper.readTree(responseBody);
            } catch (Exception e) {
                log.error("Failed to parse response as JSON for {}: {}", endpoint, e.getMessage());
                log.error("Response body (first 500 chars): {}",
                        responseBody.substring(0, Math.min(responseBody.length(), 500)));
                throw new IOException("Invalid JSON response from: " + endpoint, e);
            }

            // Log the top-level keys for debugging
            if (root.isObject()) {
                List<String> keys = new ArrayList<>();
                root.fieldNames().forEachRemaining(keys::add);
                log.debug("Response top-level keys: {}", keys);
            }

            JsonNode results = extractResultsArray(root, endpoint);
            if (results == null) {
                break;
            }

            if (results.isArray()) {
                for (JsonNode node : results) {
                    try {
                        // Clean OData metadata before deserializing
                        JsonNode cleaned = stripODataMetadata(node);
                        T entity = mapper.treeToValue(cleaned, entityClass);
                        allResults.add(entity);
                    } catch (Exception e) {
                        log.warn("Failed to parse entity of type {}: {}",
                                entityClass.getSimpleName(), e.getMessage());
                        log.debug("Problematic node: {}", node.toString().substring(0,
                                Math.min(node.toString().length(), 500)));
                    }
                }
            }

            // Handle pagination: check for __next link in all possible locations
            currentUrl = findNextPageUrl(root);
        }

        return allResults;
    }

    /**
     * Extract the results array from various OData response formats.
     *
     * Known CPI response structures:
     *   Format 1 (OData V2 standard):  {"d": {"results": [...]}}
     *   Format 2 (single entity):      {"d": {"Id": "...", ...}}
     *   Format 3 (root results):       {"results": [...]}
     *   Format 4 (root array):         [{"Id": "..."}, ...]
     *   Format 5 (value array):        {"value": [...]}
     *   Format 6 (error response):     {"error": {"code": "...", "message": {...}}}
     */
    // @author Vikas Singh | Created: 2025-11-23
    private JsonNode extractResultsArray(JsonNode root, String endpoint) throws IOException {
        // Check for OData error response
        if (root.has("error")) {
            JsonNode error = root.get("error");
            String code = error.path("code").asText("unknown");
            String message = error.path("message").path("value").asText(
                    error.path("message").asText("Unknown error"));
            log.error("OData error for {}: [{}] {}", endpoint, code, message);
            throw new IOException("OData API error for " + endpoint + ": [" + code + "] " + message);
        }

        // Format 1: {"d": {"results": [...]}}
        JsonNode dNode = root.path("d");
        if (!dNode.isMissingNode()) {
            if (dNode.has("results") && dNode.get("results").isArray()) {
                log.debug("Matched format: d.results[]");
                return dNode.get("results");
            }
            // Format 2: {"d": {"Id": "...", ...}} — single entity
            if (dNode.isObject() && !dNode.has("results")) {
                log.debug("Matched format: d.{single entity}");
                return mapper.createArrayNode().add(dNode);
            }
            // {"d": [...]} — d is itself an array
            if (dNode.isArray()) {
                log.debug("Matched format: d[]");
                return dNode;
            }
        }

        // Format 3: {"results": [...]}
        if (root.has("results") && root.get("results").isArray()) {
            log.debug("Matched format: results[]");
            return root.get("results");
        }

        // Format 5: {"value": [...]} — OData V4 style
        if (root.has("value") && root.get("value").isArray()) {
            log.debug("Matched format: value[]");
            return root.get("value");
        }

        // Format 4: root is an array
        if (root.isArray()) {
            log.debug("Matched format: root[]");
            return root;
        }

        // If nothing matched, log the full structure and bail
        log.error("Unexpected response structure for {}. Top-level type: {}",
                endpoint, root.getNodeType());
        log.error("Response (first 2000 chars): {}",
                root.toString().substring(0, Math.min(root.toString().length(), 2000)));
        return null;
    }

    /**
     * Search for the next-page URL in all possible locations within the response.
     */
    // @author Vikas Singh | Created: 2025-11-23
    private String findNextPageUrl(JsonNode root) {
        String nextUrl = null;

        // OData V2: d.__next
        JsonNode dNode = root.path("d");
        if (dNode.has("__next")) {
            nextUrl = dNode.get("__next").asText();
        }
        // Root-level __next
        else if (root.has("__next")) {
            nextUrl = root.get("__next").asText();
        }
        // OData V4: @odata.nextLink
        else if (root.has("@odata.nextLink")) {
            nextUrl = root.get("@odata.nextLink").asText();
        }

        if (nextUrl == null || nextUrl.isBlank()) {
            return null;
        }

        // Convert absolute URL to relative
        if (nextUrl.startsWith("http")) {
            String baseUrl = config.getBaseUrl();
            if (nextUrl.startsWith(baseUrl)) {
                nextUrl = nextUrl.substring(baseUrl.length());
            }
            // else keep full URL — httpClient.get() will need adjustment
        }

        // Ensure relative URLs start with /api/v1/ — SAP __next sometimes returns
        // bare entity names like "MessageProcessingLogs?$skiptoken=1000"
        if (!nextUrl.startsWith("http") && !nextUrl.startsWith("/")) {
            nextUrl = "/api/v1/" + nextUrl;
        }

        log.debug("Following pagination to: {}", nextUrl);
        return nextUrl;
    }

    /**
     * Strip OData V2 metadata fields (__metadata, __deferred, __count, etc.)
     * from a JSON node so Jackson can cleanly deserialize the entity.
     *
     * CPI OData responses contain nodes like:
     *   "__metadata": { "uri": "...", "type": "..." }
     *   "IntegrationDesigntimeArtifacts": { "__deferred": { "uri": "..." } }
     *
     * These cause Jackson to fail because they don't map to our model fields.
     */
    // @author Vikas Singh | Created: 2025-11-23
    private JsonNode stripODataMetadata(JsonNode node) {
        if (!node.isObject()) {
            return node;
        }

        ObjectNode cleaned = mapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            // Skip OData system fields
            if (key.startsWith("__")) {
                continue;
            }

            // Skip deferred navigation properties (unexpanded links)
            if (value.isObject() && value.has("__deferred")) {
                continue;
            }

            // Skip inline metadata within expanded navigation properties
            if (value.isObject() && value.has("__metadata") && !value.has("results")) {
                // This is a single expanded entity - clean it recursively
                cleaned.set(key, stripODataMetadata(value));
                continue;
            }

            // Handle expanded collection navigation properties
            if (value.isObject() && value.has("results")) {
                // This is an expanded collection — keep the results array
                cleaned.set(key, value.get("results"));
                continue;
            }

            // Keep regular fields as-is
            cleaned.set(key, value);
        }

        return cleaned;
    }
}
