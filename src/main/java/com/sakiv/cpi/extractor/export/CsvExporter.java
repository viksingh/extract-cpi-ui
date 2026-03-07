package com.sakiv.cpi.extractor.export;

import com.sakiv.cpi.extractor.model.*;
import com.sakiv.cpi.extractor.service.CpiHttpClient;
import com.sakiv.cpi.extractor.util.DateFilterUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Exports CPI extraction results to CSV files.
 * Creates separate CSV files for each entity type.
 */
public class CsvExporter {

    private static final Logger log = LoggerFactory.getLogger(CsvExporter.class);

    // @author Vikas Singh | Created: 2025-12-06
    public String export(ExtractionResult result, String outputDir, String filenamePrefix)
            throws IOException {

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        String timestamp = result.getExtractedAt()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String baseName = filenamePrefix + "_" + timestamp;

        // Export packages
        exportPackages(result, outputPath.resolve(baseName + "_packages.csv"));

        // Export flows
        exportFlows(result, outputPath.resolve(baseName + "_flows.csv"));

        // Export value mappings
        if (!result.getAllValueMappings().isEmpty()) {
            exportValueMappings(result, outputPath.resolve(baseName + "_valuemappings.csv"));
        }

        // Export configurations
        exportConfigurations(result, outputPath.resolve(baseName + "_configurations.csv"));

        // Export runtime
        if (!result.getRuntimeArtifacts().isEmpty()) {
            exportRuntime(result, outputPath.resolve(baseName + "_runtime.csv"));
        }

        // Export iFlow usage (all flows with MPL aggregation)
        if (!result.getAllFlows().isEmpty()) {
            exportIFlowUsage(result, outputPath.resolve(baseName + "_iflow_usage.csv"));
        }

        // Export ECC Endpoints
        java.util.List<IntegrationFlow> bundledFlows = result.getAllFlows().stream()
                .filter(IntegrationFlow::isBundleParsed).toList();
        if (!bundledFlows.isEmpty()) {
            exportEccEndpoints(result, outputPath.resolve(baseName + "_ecc_endpoints.csv"));
            exportFlowChains(result, outputPath.resolve(baseName + "_flow_chains.csv"));
        }

        // Export raw message processing logs
        if (result.getMessageProcessingLogs() != null && !result.getMessageProcessingLogs().isEmpty()) {
            exportMessageLogs(result, outputPath.resolve(baseName + "_message_logs.csv"));
        }

        // Export API calls
        if (!result.getApiCallLog().isEmpty()) {
            exportApiCalls(result, outputPath.resolve(baseName + "_apicalls.csv"));
        }

        log.info("CSV files exported to: {}", outputPath.toAbsolutePath());
        return outputPath.toAbsolutePath().toString();
    }

    // @author Vikas Singh | Created: 2025-12-07
    private void exportPackages(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Package ID", "Name", "Description", "Version", "Vendor",
                                "Mode", "Created By", "Creation Date", "Modified By",
                                "Modified Date", "Products", "Keywords")
                        .build())) {
            for (IntegrationPackage pkg : result.getPackages()) {
                printer.printRecord(
                        pkg.getId(), pkg.getName(), pkg.getDescription(), pkg.getVersion(),
                        pkg.getVendor(), pkg.getMode(), pkg.getCreatedBy(), fmt(pkg.getCreationDate()),
                        pkg.getModifiedBy(), fmt(pkg.getModifiedDate()), pkg.getProducts(),
                        pkg.getKeywords());
            }
        }
    }

    // @author Vikas Singh | Created: 2025-12-07
    private void exportFlows(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Flow ID", "Name", "Description", "Package ID", "Version",
                                "Sender", "Receiver", "Created By", "Created At",
                                "Modified By", "Modified At", "Runtime Status",
                                "Deployed Version", "Deployed By", "Error Info")
                        .build())) {
            for (IntegrationFlow flow : result.getAllFlows()) {
                printer.printRecord(
                        flow.getId(), flow.getName(), flow.getDescription(), flow.getPackageId(),
                        flow.getVersion(), flow.getSender(), flow.getReceiver(),
                        flow.getCreatedBy(), fmt(flow.getCreatedAt()), flow.getModifiedBy(),
                        fmt(flow.getModifiedAt()), flow.getRuntimeStatus(), flow.getDeployedVersion(),
                        flow.getDeployedBy(), flow.getRuntimeError());
            }
        }
    }

    // @author Vikas Singh | Created: 2025-12-07
    private void exportValueMappings(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("ID", "Name", "Description", "Package ID", "Version",
                                "Created By", "Created At", "Modified By", "Modified At")
                        .build())) {
            for (ValueMapping vm : result.getAllValueMappings()) {
                printer.printRecord(
                        vm.getId(), vm.getName(), vm.getDescription(), vm.getPackageId(),
                        vm.getVersion(), vm.getCreatedBy(), fmt(vm.getCreatedAt()),
                        vm.getModifiedBy(), fmt(vm.getModifiedAt()));
            }
        }
    }

    // @author Vikas Singh | Created: 2025-12-07
    private void exportConfigurations(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Artifact ID", "Artifact Name", "Parameter Key",
                                "Parameter Value", "Data Type")
                        .build())) {
            for (IntegrationFlow flow : result.getAllFlows()) {
                for (Configuration cfg : flow.getConfigurations()) {
                    printer.printRecord(
                            flow.getId(), flow.getName(), cfg.getParameterKey(),
                            cfg.getParameterValue(), cfg.getDataType());
                }
            }
        }
    }

    private void exportApiCalls(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("#", "Method", "URL / Path", "Status Code", "Duration (ms)")
                        .build())) {
            int seq = 1;
            for (CpiHttpClient.ApiCallRecord call : result.getApiCallLog()) {
                printer.printRecord(seq++, call.method(), call.path(),
                        call.statusCode(), call.durationMs());
            }
        }
    }

    private void exportIFlowUsage(ExtractionResult result, Path filePath) throws IOException {
        // Build MPL lookup by flow name
        java.util.Map<String, java.util.List<MessageProcessingLog>> mplByFlow = new java.util.LinkedHashMap<>();
        if (result.getMessageProcessingLogs() != null) {
            for (MessageProcessingLog mpl : result.getMessageProcessingLogs()) {
                String name = mpl.getIntegrationFlowName();
                if (name != null && !name.isBlank()) {
                    mplByFlow.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(mpl);
                }
            }
        }

        // Build flow-to-package mapping
        java.util.Map<String, String> flowToPackage = new java.util.LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                String flowName = f.getName() != null ? f.getName() : f.getId();
                if (flowName != null) {
                    flowToPackage.put(flowName, pkg.getName() != null ? pkg.getName() : pkg.getId());
                }
            }
        }

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Package", "iFlow Name", "Total", "Completed", "Failed", "Retry",
                                "Escalated", "Last Execution", "Last Status")
                        .build())) {
            for (IntegrationFlow flow : result.getAllFlows()) {
                String flowName = flow.getName() != null ? flow.getName() : flow.getId();
                String flowId = flow.getId() != null ? flow.getId() : flowName;
                if (flowName == null) continue;
                String pkgName = flowToPackage.getOrDefault(flowName, "");

                java.util.List<MessageProcessingLog> logs = mplByFlow.get(flowId);
                if ((logs == null || logs.isEmpty()) && !flowId.equals(flowName)) {
                    logs = mplByFlow.get(flowName);
                }
                if (logs == null || logs.isEmpty()) {
                    printer.printRecord(pkgName, flowName, 0, 0, 0, 0, 0, "", "Not Used");
                } else {
                    int total = logs.size();
                    int completed = 0, failed = 0, retry = 0, escalated = 0;
                    String lastExec = "";
                    String lastStatus = "";
                    for (MessageProcessingLog m : logs) {
                        String s = m.getStatus() != null ? m.getStatus().toUpperCase() : "";
                        switch (s) {
                            case "COMPLETED" -> completed++;
                            case "FAILED" -> failed++;
                            case "RETRY" -> retry++;
                            case "ESCALATED" -> escalated++;
                        }
                        String logEnd = m.getLogEnd() != null ? m.getLogEnd() : "";
                        if (logEnd.compareTo(lastExec) > 0) {
                            lastExec = logEnd;
                            lastStatus = m.getStatus() != null ? m.getStatus() : "";
                        }
                    }
                    printer.printRecord(pkgName, flowName, total, completed, failed, retry,
                            escalated, fmt(lastExec), lastStatus);
                }
            }
        }
    }

    private void exportMessageLogs(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Message GUID", "iFlow Name", "Status", "Log Start", "Log End",
                                "Sender", "Receiver", "Application Message ID",
                                "Correlation ID", "Log Level")
                        .build())) {
            for (MessageProcessingLog mpl : result.getMessageProcessingLogs()) {
                printer.printRecord(
                        mpl.getMessageGuid(), mpl.getIntegrationFlowName(), mpl.getStatus(),
                        fmt(mpl.getLogStart()), fmt(mpl.getLogEnd()), mpl.getSender(), mpl.getReceiver(),
                        mpl.getApplicationMessageId(), mpl.getCorrelationId(), mpl.getLogLevel());
            }
        }
    }

    private String fmt(String cpiDate) {
        return DateFilterUtil.formatCpiDate(cpiDate);
    }

    // @author Vikas Singh | Created: 2025-12-07
    private void exportRuntime(ExtractionResult result, Path filePath) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Artifact ID", "Name", "Type", "Version", "Status",
                                "Deployed By", "Deployed On", "Error Information")
                        .build())) {
            for (RuntimeArtifact rt : result.getRuntimeArtifacts()) {
                printer.printRecord(
                        rt.getId(), rt.getName(), rt.getType(), rt.getVersion(),
                        rt.getStatus(), rt.getDeployedBy(), fmt(rt.getDeployedOn()),
                        rt.getErrorInformation());
            }
        }
    }

    private void exportEccEndpoints(ExtractionResult result, Path filePath) throws IOException {
        java.util.Map<String, String> flowToPackage = new java.util.LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                String flowName = f.getName() != null ? f.getName() : f.getId();
                flowToPackage.put(flowName, pkg.getName() != null ? pkg.getName() : pkg.getId());
            }
        }

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Package", "iFlow", "Direction", "Adapter Type",
                                "Transport Protocol", "Message Protocol", "Address", "Category")
                        .build())) {
            for (IntegrationFlow flow : result.getAllFlows()) {
                if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
                String flowName = flow.getName() != null ? flow.getName() : flow.getId();
                String pkgName = flowToPackage.getOrDefault(flowName, "");

                for (var adapter : flow.getIflowContent().getAdapters()) {
                    String type = adapter.getAdapterType() != null ? adapter.getAdapterType() : "";
                    String proto = adapter.getTransportProtocol() != null ? adapter.getTransportProtocol() : "";
                    String msgProto = adapter.getMessageProtocol() != null ? adapter.getMessageProtocol() : "";
                    String category = classifyEndpoint(type, proto, msgProto);
                    printer.printRecord(pkgName, flowName, adapter.getDirection(), type,
                            proto, msgProto, adapter.getAddress(), category);
                }
            }
        }
    }

    private void exportFlowChains(ExtractionResult result, Path filePath) throws IOException {
        java.util.Map<String, String> flowToPackage = new java.util.LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                flowToPackage.put(f.getId(), pkg.getName() != null ? pkg.getName() : pkg.getId());
            }
        }

        java.util.Map<String, java.util.List<String[]>> jmsProducers = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<String[]>> jmsConsumers = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<String[]>> pdProducers = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<String[]>> pdConsumers = new java.util.LinkedHashMap<>();

        for (IntegrationFlow flow : result.getAllFlows()) {
            if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
            String flowName = flow.getName() != null ? flow.getName() : flow.getId();
            for (var adapter : flow.getIflowContent().getAdapters()) {
                String type = adapter.getAdapterType() != null ? adapter.getAdapterType().toLowerCase() : "";
                String address = resolveChainAddress(adapter, type);
                String dir = adapter.getDirection() != null ? adapter.getDirection() : "";
                if (address.isEmpty()) continue;
                String[] info = { flow.getId(), flowName };
                if (type.contains("jms")) {
                    if ("Receiver".equalsIgnoreCase(dir))
                        jmsProducers.computeIfAbsent(address, k -> new java.util.ArrayList<>()).add(info);
                    else if ("Sender".equalsIgnoreCase(dir))
                        jmsConsumers.computeIfAbsent(address, k -> new java.util.ArrayList<>()).add(info);
                } else if (type.contains("processdirect")) {
                    if ("Receiver".equalsIgnoreCase(dir))
                        pdProducers.computeIfAbsent(address, k -> new java.util.ArrayList<>()).add(info);
                    else if ("Sender".equalsIgnoreCase(dir))
                        pdConsumers.computeIfAbsent(address, k -> new java.util.ArrayList<>()).add(info);
                }
            }
        }

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(filePath.toFile()),
                CSVFormat.DEFAULT.builder()
                        .setHeader("Chain Type", "Queue / Address", "Sender iFlow",
                                "Sender Package", "Receiver iFlow", "Receiver Package")
                        .build())) {
            writeChainCsv(printer, "JMS", jmsProducers, jmsConsumers, flowToPackage);
            writeChainCsv(printer, "ProcessDirect", pdProducers, pdConsumers, flowToPackage);
        }
    }

    private void writeChainCsv(CSVPrinter printer, String chainType,
                                java.util.Map<String, java.util.List<String[]>> producers,
                                java.util.Map<String, java.util.List<String[]>> consumers,
                                java.util.Map<String, String> flowToPackage) throws IOException {
        for (var entry : producers.entrySet()) {
            String queue = entry.getKey();
            java.util.List<String[]> cons = consumers.getOrDefault(queue, java.util.List.of());
            if (cons.isEmpty()) {
                for (String[] prod : entry.getValue())
                    printer.printRecord(chainType, queue, prod[1],
                            flowToPackage.getOrDefault(prod[0], ""), "(no consumer found)", "");
            } else {
                for (String[] prod : entry.getValue())
                    for (String[] con : cons)
                        printer.printRecord(chainType, queue, prod[1],
                                flowToPackage.getOrDefault(prod[0], ""), con[1],
                                flowToPackage.getOrDefault(con[0], ""));
            }
        }
        // Orphan consumers
        for (var entry : consumers.entrySet()) {
            if (!producers.containsKey(entry.getKey())) {
                for (String[] cons : entry.getValue())
                    printer.printRecord(chainType, entry.getKey(), "(no producer found)", "",
                            cons[1], flowToPackage.getOrDefault(cons[0], ""));
            }
        }
    }

    private static String classifyEndpoint(String adapterType, String transportProtocol, String messageProtocol) {
        String type = adapterType.toLowerCase();
        String proto = transportProtocol.toLowerCase();
        String msgProto = messageProtocol.toLowerCase();

        if (type.contains("idoc") || msgProto.contains("idoc")) return "ECC (IDoc)";
        if (type.contains("rfc") || proto.contains("rfc")) return "ECC (RFC/BAPI)";
        if (type.contains("xi") || type.contains("soap") && msgProto.contains("xi")) return "ECC (XI/SOAP)";
        if (type.contains("as2")) return "Legacy (AS2)";
        if (type.contains("odata") || proto.contains("odata")) return "S/4 Compatible (OData)";
        if (type.contains("http") || type.contains("rest")) return "S/4 Compatible (HTTP/REST)";
        if (type.contains("soap")) return "Neutral (SOAP)";
        if (type.contains("jms")) return "Middleware (JMS)";
        if (type.contains("processdirect")) return "Internal (ProcessDirect)";
        if (type.contains("sftp") || type.contains("ftp")) return "Neutral (SFTP/FTP)";
        if (type.contains("mail") || type.contains("smtp") || type.contains("imap")) return "Neutral (Mail)";
        if (type.contains("kafka")) return "Neutral (Kafka)";
        if (type.contains("amqp")) return "Neutral (AMQP)";
        return "Other (" + adapterType + ")";
    }

    private static String resolveChainAddress(IFlowAdapter adapter, String typeLower) {
        java.util.Map<String, String> props = adapter.getProperties();
        String dir = adapter.getDirection() != null ? adapter.getDirection() : "";
        if (typeLower.contains("jms")) {
            for (String key : java.util.List.of(
                    "Receiver".equalsIgnoreCase(dir) ? "QueueName_outbound" : "QueueName_inbound",
                    "QueueName_outbound", "QueueName_inbound",
                    "Destination", "QueueName", "destination", "queueName")) {
                String val = props.get(key);
                if (val != null && !val.isBlank()) return val;
            }
        }
        if (typeLower.contains("processdirect")) {
            for (String key : java.util.List.of("address", "Address", "ProcessDirectAddress")) {
                String val = props.get(key);
                if (val != null && !val.isBlank()) return val;
            }
        }
        if (adapter.getAddress() != null && !adapter.getAddress().isBlank()) return adapter.getAddress();
        for (var entry : props.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("destination") || key.contains("queue") || key.contains("address")) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) return entry.getValue();
            }
        }
        return "";
    }
}
