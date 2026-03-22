
package com.sakiv.cpi.extractor.export;

import com.sakiv.cpi.extractor.model.*;
import com.sakiv.cpi.extractor.util.DateFilterUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;

import com.sakiv.cpi.extractor.service.CpiHttpClient;

import com.sakiv.cpi.extractor.service.DependencyAnalysisService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports CPI extraction results to Excel (.xlsx) format
 * with separate sheets for packages, flows, configurations, and runtime status.
 */
public class ExcelExporter {

    private static final Logger log = LoggerFactory.getLogger(ExcelExporter.class);
    private static final int MAX_CELL_LENGTH = 32767;

    // @author Vikas Singh | Created: 2025-12-13
    public String export(ExtractionResult result, String outputDir, String filenamePrefix)
            throws IOException {

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        String timestamp = result.getExtractedAt()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = filenamePrefix + "_" + timestamp + ".xlsx";
        Path filePath = outputPath.resolve(filename);

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle wrapStyle = createWrapStyle(workbook);

            // Sheet 1: Summary
            createSummarySheet(workbook, headerStyle, result);

            // Sheet 2: Integration Packages
            createPackagesSheet(workbook, headerStyle, result.getPackages());

            // Sheet 3: Integration Flows
            createFlowsSheet(workbook, headerStyle, result.getAllFlows());

            // Sheet 4: Value Mappings
            if (!result.getAllValueMappings().isEmpty()) {
                createValueMappingsSheet(workbook, headerStyle, result.getAllValueMappings());
            }

            // Sheet 5: Configurations
            createConfigurationsSheet(workbook, headerStyle, result.getAllFlows());

            // Sheet 6: Runtime Status
            if (!result.getRuntimeArtifacts().isEmpty()) {
                createRuntimeSheet(workbook, headerStyle, result.getRuntimeArtifacts());
            }

            // Sheet 7-9: iFlow Bundle content (adapters, mappings, scripts) — only when bundles were extracted
            List<IntegrationFlow> bundledFlows = result.getAllFlows().stream()
                    .filter(IntegrationFlow::isBundleParsed)
                    .toList();
            if (!bundledFlows.isEmpty()) {
                createAdaptersSheet(workbook, headerStyle, bundledFlows);
                createMappingsSheet(workbook, headerStyle, bundledFlows);
                createScriptsSheet(workbook, headerStyle, bundledFlows);
            }

            // Sheet: iFlow Usage (all flows with MPL aggregation)
            if (!result.getAllFlows().isEmpty()) {
                createIFlowUsageSheet(workbook, headerStyle, result);
            }

            // Sheet: ECC Endpoints
            if (!bundledFlows.isEmpty()) {
                createEccEndpointsSheet(workbook, headerStyle, result);
            }

            // Sheet: Flow Chains (JMS / ProcessDirect)
            if (!bundledFlows.isEmpty()) {
                createFlowChainsSheet(workbook, headerStyle, result);
            }

            // Sheet: Dependencies (Package + Flow level)
            if (!bundledFlows.isEmpty()) {
                createDependencySheets(workbook, headerStyle, result);
            }

            // Sheet: Credentials / Security Materials (E4)
            if (!bundledFlows.isEmpty()) {
                createCredentialsSheet(workbook, headerStyle, result);
            }

            // Sheet: Message Processing Logs (raw)
            if (result.getMessageProcessingLogs() != null && !result.getMessageProcessingLogs().isEmpty()) {
                createMessageLogsSheet(workbook, headerStyle, result.getMessageProcessingLogs());
            }

            // Sheet: API Calls
            if (!result.getApiCallLog().isEmpty()) {
                createApiCallsSheet(workbook, headerStyle, result.getApiCallLog());
            }

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        log.info("Excel report exported to: {}", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }

    // =========================================================================
    // Sheet Creators
    // =========================================================================

    // @author Vikas Singh | Created: 2025-12-13
    private void createSummarySheet(Workbook wb, CellStyle headerStyle, ExtractionResult result) {
        Sheet sheet = wb.createSheet("Summary");
        int rowNum = 0;

        createTitleRow(sheet, headerStyle, rowNum++, "SAP CPI Artifact Extraction Summary");
        rowNum++;

        addSummaryRow(sheet, rowNum++, "Tenant URL", result.getTenantUrl());
        addSummaryRow(sheet, rowNum++, "Extracted At",
                result.getExtractedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        rowNum++;
        addSummaryRow(sheet, rowNum++, "Integration Packages", String.valueOf(result.getPackages().size()));
        addSummaryRow(sheet, rowNum++, "Integration Flows", String.valueOf(result.getAllFlows().size()));
        addSummaryRow(sheet, rowNum++, "Value Mappings", String.valueOf(result.getAllValueMappings().size()));
        addSummaryRow(sheet, rowNum++, "Runtime Artifacts", String.valueOf(result.getRuntimeArtifacts().size()));
        addSummaryRow(sheet, rowNum++, "Message Processing Logs",
                String.valueOf(result.getMessageProcessingLogs() != null ? result.getMessageProcessingLogs().size() : 0));

        long started = result.getRuntimeArtifacts().stream()
                .filter(r -> "STARTED".equalsIgnoreCase(r.getStatus())).count();
        long errors = result.getRuntimeArtifacts().stream()
                .filter(r -> "ERROR".equalsIgnoreCase(r.getStatus())).count();
        addSummaryRow(sheet, rowNum++, "  - STARTED", String.valueOf(started));
        addSummaryRow(sheet, rowNum++, "  - ERROR", String.valueOf(errors));

        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 15000);
    }

    // @author Vikas Singh | Created: 2025-12-13
    private void createPackagesSheet(Workbook wb, CellStyle headerStyle,
                                      List<IntegrationPackage> packages) {
        Sheet sheet = wb.createSheet("Packages");
        String[] headers = {
                "Package ID", "Name", "Description", "Version", "Vendor", "Mode",
                "Created By", "Creation Date", "Modified By", "Modified Date",
                "Products", "Keywords", "# Flows", "# Value Mappings"
        };
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (IntegrationPackage pkg : packages) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(pkg.getId()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getName()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getDescription()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getVersion()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getVendor()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getMode()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getCreatedBy()));
            row.createCell(col++).setCellValue(formatCpiDate(pkg.getCreationDate()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getModifiedBy()));
            row.createCell(col++).setCellValue(formatCpiDate(pkg.getModifiedDate()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getProducts()));
            row.createCell(col++).setCellValue(nullSafe(pkg.getKeywords()));
            row.createCell(col++).setCellValue(pkg.getIntegrationFlows().size());
            row.createCell(col++).setCellValue(pkg.getValueMappings().size());
        }

        autoSizeColumns(sheet, headers.length);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    // @author Vikas Singh | Created: 2025-12-13
    private void createFlowsSheet(Workbook wb, CellStyle headerStyle,
                                    List<IntegrationFlow> flows) {
        Sheet sheet = wb.createSheet("Integration Flows");
        String[] headers = {
                "Flow ID", "Name", "Description", "Package ID", "Version",
                "Sender", "Receiver", "Created By", "Created At",
                "Modified By", "Modified At", "Runtime Status",
                "Deployed Version", "Deployed By", "Deployed At", "Error Info"
        };
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (IntegrationFlow flow : flows) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(flow.getId()));
            row.createCell(col++).setCellValue(nullSafe(flow.getName()));
            row.createCell(col++).setCellValue(nullSafe(flow.getDescription()));
            row.createCell(col++).setCellValue(nullSafe(flow.getPackageId()));
            row.createCell(col++).setCellValue(nullSafe(flow.getVersion()));
            row.createCell(col++).setCellValue(nullSafe(flow.getSender()));
            row.createCell(col++).setCellValue(nullSafe(flow.getReceiver()));
            row.createCell(col++).setCellValue(nullSafe(flow.getCreatedBy()));
            row.createCell(col++).setCellValue(formatCpiDate(flow.getCreatedAt()));
            row.createCell(col++).setCellValue(nullSafe(flow.getModifiedBy()));
            row.createCell(col++).setCellValue(formatCpiDate(flow.getModifiedAt()));
            row.createCell(col++).setCellValue(nullSafe(flow.getRuntimeStatus()));
            row.createCell(col++).setCellValue(nullSafe(flow.getDeployedVersion()));
            row.createCell(col++).setCellValue(nullSafe(flow.getDeployedBy()));
            row.createCell(col++).setCellValue(nullSafe(flow.getDeployedAt()));
            row.createCell(col++).setCellValue(nullSafe(flow.getRuntimeError()));
        }

        autoSizeColumns(sheet, headers.length);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void createValueMappingsSheet(Workbook wb, CellStyle headerStyle,
                                           List<ValueMapping> mappings) {
        Sheet sheet = wb.createSheet("Value Mappings");
        String[] headers = {
                "ID", "Name", "Description", "Package ID", "Version",
                "Created By", "Created At", "Modified By", "Modified At", "Runtime Status"
        };
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (ValueMapping vm : mappings) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(vm.getId()));
            row.createCell(col++).setCellValue(nullSafe(vm.getName()));
            row.createCell(col++).setCellValue(nullSafe(vm.getDescription()));
            row.createCell(col++).setCellValue(nullSafe(vm.getPackageId()));
            row.createCell(col++).setCellValue(nullSafe(vm.getVersion()));
            row.createCell(col++).setCellValue(nullSafe(vm.getCreatedBy()));
            row.createCell(col++).setCellValue(formatCpiDate(vm.getCreatedAt()));
            row.createCell(col++).setCellValue(nullSafe(vm.getModifiedBy()));
            row.createCell(col++).setCellValue(formatCpiDate(vm.getModifiedAt()));
            row.createCell(col++).setCellValue(nullSafe(vm.getRuntimeStatus()));
        }

        autoSizeColumns(sheet, headers.length);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void createConfigurationsSheet(Workbook wb, CellStyle headerStyle,
                                            List<IntegrationFlow> flows) {
        Sheet sheet = wb.createSheet("Configurations");
        String[] headers = {"Artifact ID", "Artifact Name", "Parameter Key", "Parameter Value", "Data Type"};
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (IntegrationFlow flow : flows) {
            for (Configuration cfg : flow.getConfigurations()) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(nullSafe(flow.getId()));
                row.createCell(col++).setCellValue(nullSafe(flow.getName()));
                row.createCell(col++).setCellValue(nullSafe(cfg.getParameterKey()));
                row.createCell(col++).setCellValue(nullSafe(cfg.getParameterValue()));
                row.createCell(col++).setCellValue(nullSafe(cfg.getDataType()));
            }
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void createRuntimeSheet(Workbook wb, CellStyle headerStyle,
                                     List<RuntimeArtifact> artifacts) {
        Sheet sheet = wb.createSheet("Runtime Status");
        String[] headers = {
                "Artifact ID", "Name", "Type", "Version", "Status",
                "Deployed By", "Deployed On", "Error Information"
        };
        createHeaderRow(sheet, headerStyle, headers);

        // Create conditional formatting style for errors
        CellStyle errorStyle = wb.createCellStyle();
        Font errorFont = wb.createFont();
        errorFont.setColor(IndexedColors.RED.getIndex());
        errorFont.setBold(true);
        errorStyle.setFont(errorFont);

        CellStyle successStyle = wb.createCellStyle();
        Font successFont = wb.createFont();
        successFont.setColor(IndexedColors.GREEN.getIndex());
        successFont.setBold(true);
        successStyle.setFont(successFont);

        int rowNum = 1;
        for (RuntimeArtifact rt : artifacts) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(rt.getId()));
            row.createCell(col++).setCellValue(nullSafe(rt.getName()));
            row.createCell(col++).setCellValue(nullSafe(rt.getType()));
            row.createCell(col++).setCellValue(nullSafe(rt.getVersion()));

            Cell statusCell = row.createCell(col++);
            statusCell.setCellValue(nullSafe(rt.getStatus()));
            if ("ERROR".equalsIgnoreCase(rt.getStatus())) {
                statusCell.setCellStyle(errorStyle);
            } else if ("STARTED".equalsIgnoreCase(rt.getStatus())) {
                statusCell.setCellStyle(successStyle);
            }

            row.createCell(col++).setCellValue(nullSafe(rt.getDeployedBy()));
            row.createCell(col++).setCellValue(nullSafe(rt.getDeployedOn()));
            row.createCell(col++).setCellValue(nullSafe(rt.getErrorInformation()));
        }

        autoSizeColumns(sheet, headers.length);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    // @author Vikas Singh | Created: 2026-02-22
    private void createAdaptersSheet(Workbook wb, CellStyle headerStyle,
                                      List<IntegrationFlow> bundledFlows) {
        Sheet sheet = wb.createSheet("iFlow Adapters");
        String[] headers = {
                "Flow ID", "Flow Name", "Adapter ID", "Adapter Name",
                "Direction", "Adapter Type", "Transport Protocol", "Message Protocol", "Address"
        };
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (IntegrationFlow flow : bundledFlows) {
            IFlowContent content = flow.getIflowContent();
            if (content == null) continue;
            for (IFlowAdapter adapter : content.getAdapters()) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(nullSafe(flow.getId()));
                row.createCell(col++).setCellValue(nullSafe(flow.getName()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getId()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getName()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getDirection()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getAdapterType()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getTransportProtocol()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getMessageProtocol()));
                row.createCell(col++).setCellValue(nullSafe(adapter.getAddress()));
            }
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    // @author Vikas Singh | Created: 2026-02-22
    private void createMappingsSheet(Workbook wb, CellStyle headerStyle,
                                      List<IntegrationFlow> bundledFlows) {
        Sheet sheet = wb.createSheet("iFlow Mappings");
        String[] headers = {
                "Flow ID", "Flow Name", "Mapping ID", "Mapping Name", "Mapping Type", "Resource ID"
        };
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (IntegrationFlow flow : bundledFlows) {
            IFlowContent content = flow.getIflowContent();
            if (content == null) continue;
            for (IFlowMapping mapping : content.getMappings()) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(nullSafe(flow.getId()));
                row.createCell(col++).setCellValue(nullSafe(flow.getName()));
                row.createCell(col++).setCellValue(nullSafe(mapping.getId()));
                row.createCell(col++).setCellValue(nullSafe(mapping.getName()));
                row.createCell(col++).setCellValue(nullSafe(mapping.getMappingType()));
                row.createCell(col++).setCellValue(nullSafe(mapping.getResourceId()));
            }
            // Also list mapping files (mmap, xsl) from the bundle
            for (String mfile : content.getMappingFiles()) {
                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(nullSafe(flow.getId()));
                row.createCell(col++).setCellValue(nullSafe(flow.getName()));
                row.createCell(col++).setCellValue("");
                row.createCell(col++).setCellValue(nullSafe(mfile));
                row.createCell(col++).setCellValue("File");
                row.createCell(col++).setCellValue("");
            }
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    // @author Vikas Singh | Created: 2026-02-22
    private void createScriptsSheet(Workbook wb, CellStyle headerStyle,
                                     List<IntegrationFlow> bundledFlows) {
        Sheet sheet = wb.createSheet("iFlow Scripts");
        String[] headers = {
                "Flow ID", "Flow Name", "Script File", "Language", "Script Content (Snippet)"
        };
        createHeaderRow(sheet, headerStyle, headers);

        CellStyle wrapStyle = createWrapStyle(wb);

        int rowNum = 1;
        for (IntegrationFlow flow : bundledFlows) {
            IFlowContent content = flow.getIflowContent();
            if (content == null) continue;
            for (ScriptInfo script : content.getScripts()) {
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(60);
                int col = 0;
                row.createCell(col++).setCellValue(nullSafe(flow.getId()));
                row.createCell(col++).setCellValue(nullSafe(flow.getName()));
                row.createCell(col++).setCellValue(nullSafe(script.getFileName()));
                row.createCell(col++).setCellValue(nullSafe(script.getLanguage()));
                Cell snippetCell = row.createCell(col);
                snippetCell.setCellValue(nullSafe(script.getContentSnippet()));
                snippetCell.setCellStyle(wrapStyle);
            }
        }

        // Fix column widths — snippet column gets a wider fixed width
        for (int i = 0; i < headers.length - 1; i++) {
            sheet.autoSizeColumn(i);
            if (sheet.getColumnWidth(i) > 10000) {
                sheet.setColumnWidth(i, 10000);
            }
        }
        sheet.setColumnWidth(headers.length - 1, 20000); // snippet column

        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    private void createIFlowUsageSheet(Workbook wb, CellStyle headerStyle, ExtractionResult result) {
        Sheet sheet = wb.createSheet("iFlow Usage");
        String[] headers = {
                "Package", "iFlow Name", "Total", "Completed", "Failed", "Retry", "Escalated",
                "Last Execution", "Last Status", "Runtime Status", "Deployed Status"
        };
        createHeaderRow(sheet, headerStyle, headers);

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

        CellStyle errorStyle = wb.createCellStyle();
        Font errorFont = wb.createFont();
        errorFont.setColor(IndexedColors.RED.getIndex());
        errorFont.setBold(true);
        errorStyle.setFont(errorFont);

        CellStyle notUsedStyle = wb.createCellStyle();
        Font notUsedFont = wb.createFont();
        notUsedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        notUsedFont.setItalic(true);
        notUsedStyle.setFont(notUsedFont);

        CellStyle unusedDeployedStyle = wb.createCellStyle();
        Font unusedDeployedFont = wb.createFont();
        unusedDeployedFont.setColor(IndexedColors.ORANGE.getIndex());
        unusedDeployedFont.setBold(true);
        unusedDeployedStyle.setFont(unusedDeployedFont);

        int rowNum = 1;
        for (IntegrationFlow flow : result.getAllFlows()) {
            String flowName = flow.getName() != null ? flow.getName() : flow.getId();
            String flowId = flow.getId() != null ? flow.getId() : flowName;
            if (flowName == null) continue;
            String pkgName = flowToPackage.getOrDefault(flowName, "");

            java.util.List<MessageProcessingLog> logs = mplByFlow.get(flowId);
            if ((logs == null || logs.isEmpty()) && !flowId.equals(flowName)) {
                logs = mplByFlow.get(flowName);
            }

            // E2: Compute deployed status
            String runtimeStatus = flow.getRuntimeStatus() != null ? flow.getRuntimeStatus() : "UNKNOWN";
            boolean noLogs = (logs == null || logs.isEmpty());
            String deployedStatus;
            if ("STARTED".equalsIgnoreCase(runtimeStatus) && noLogs) {
                deployedStatus = "Unused Deployed";
            } else if ("STARTED".equalsIgnoreCase(runtimeStatus)) {
                deployedStatus = "Active";
            } else if ("NOT_DEPLOYED".equalsIgnoreCase(runtimeStatus)) {
                deployedStatus = "Not Deployed";
            } else if ("ERROR".equalsIgnoreCase(runtimeStatus)) {
                deployedStatus = "Error";
            } else {
                deployedStatus = runtimeStatus;
            }

            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(pkgName));
            row.createCell(col++).setCellValue(nullSafe(flowName));

            if (noLogs) {
                row.createCell(col++).setCellValue(0);
                row.createCell(col++).setCellValue(0);
                row.createCell(col++).setCellValue(0);
                row.createCell(col++).setCellValue(0);
                row.createCell(col++).setCellValue(0);
                row.createCell(col++).setCellValue("");
                Cell statusCell = row.createCell(col++);
                statusCell.setCellValue("Not Used");
                statusCell.setCellStyle(notUsedStyle);
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
                row.createCell(col++).setCellValue(total);
                row.createCell(col++).setCellValue(completed);
                Cell failedCell = row.createCell(col++);
                failedCell.setCellValue(failed);
                if (failed > 0) failedCell.setCellStyle(errorStyle);
                row.createCell(col++).setCellValue(retry);
                row.createCell(col++).setCellValue(escalated);
                row.createCell(col++).setCellValue(formatCpiDate(lastExec));
                row.createCell(col++).setCellValue(nullSafe(lastStatus));
            }
            // E2: Runtime Status and Deployed Status columns
            row.createCell(col++).setCellValue(nullSafe(runtimeStatus));
            Cell deployedStatusCell = row.createCell(col++);
            deployedStatusCell.setCellValue(deployedStatus);
            if ("Unused Deployed".equals(deployedStatus)) {
                deployedStatusCell.setCellStyle(unusedDeployedStyle);
            }
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    private void createMessageLogsSheet(Workbook wb, CellStyle headerStyle,
                                         java.util.List<MessageProcessingLog> logs) {
        Sheet sheet = wb.createSheet("Message Processing Logs");
        String[] headers = {
                "Message GUID", "iFlow Name", "Status", "Log Start", "Log End",
                "Sender", "Receiver", "Application Message ID", "Correlation ID", "Log Level"
        };
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (MessageProcessingLog mpl : logs) {
            Row row = sheet.createRow(rowNum++);
            int col = 0;
            row.createCell(col++).setCellValue(nullSafe(mpl.getMessageGuid()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getIntegrationFlowName()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getStatus()));
            row.createCell(col++).setCellValue(formatCpiDate(mpl.getLogStart()));
            row.createCell(col++).setCellValue(formatCpiDate(mpl.getLogEnd()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getSender()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getReceiver()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getApplicationMessageId()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getCorrelationId()));
            row.createCell(col++).setCellValue(nullSafe(mpl.getLogLevel()));
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    private void createApiCallsSheet(Workbook wb, CellStyle headerStyle,
                                      List<CpiHttpClient.ApiCallRecord> apiCalls) {
        Sheet sheet = wb.createSheet("API Calls");
        String[] headers = {"#", "Method", "URL / Path", "Status Code", "Duration (ms)"};
        createHeaderRow(sheet, headerStyle, headers);

        int rowNum = 1;
        for (CpiHttpClient.ApiCallRecord call : apiCalls) {
            Row row = sheet.createRow(rowNum);
            int col = 0;
            row.createCell(col++).setCellValue(rowNum);
            row.createCell(col++).setCellValue(call.method());
            row.createCell(col++).setCellValue(nullSafe(call.path()));
            row.createCell(col++).setCellValue(call.statusCode());
            row.createCell(col++).setCellValue(call.durationMs());
            rowNum++;
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    // @author Vikas Singh | Created: 2025-12-14
    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    // @author Vikas Singh | Created: 2025-12-14
    private CellStyle createWrapStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setWrapText(true);
        return style;
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void createHeaderRow(Sheet sheet, CellStyle style, String[] headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void createTitleRow(Sheet sheet, CellStyle style, int rowNum, String title) {
        Row row = sheet.createRow(rowNum);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(style);
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void addSummaryRow(Sheet sheet, int rowNum, String label, String value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    // @author Vikas Singh | Created: 2025-12-14
    private void autoSizeColumns(Sheet sheet, int numColumns) {
        for (int i = 0; i < numColumns; i++) {
            sheet.autoSizeColumn(i);
            // Cap width at 15000 to prevent very wide columns
            if (sheet.getColumnWidth(i) > 15000) {
                sheet.setColumnWidth(i, 15000);
            }
        }
    }

    // @author Vikas Singh | Created: 2025-12-14
    private String formatCpiDate(String cpiDate) {
        return DateFilterUtil.formatCpiDate(cpiDate);
    }

    // @author Vikas Singh | Created: 2025-12-14
    private String nullSafe(String value) {
        if (value == null) return "";
        if (value.length() > MAX_CELL_LENGTH) {
            log.warn("Truncating cell value from {} to {} characters", value.length(), MAX_CELL_LENGTH);
            return value.substring(0, MAX_CELL_LENGTH - 3) + "...";
        }
        return value;
    }

    // =========================================================================
    // Dependency Sheets (Package + Flow level)
    // =========================================================================

    private void createDependencySheets(Workbook wb, CellStyle headerStyle, ExtractionResult result) {
        DependencyAnalysisService analysisService = new DependencyAnalysisService();
        DependencyGraph graph = analysisService.analyze(result, null);

        if (graph.getDependencies().isEmpty()) return;

        // Build packageId -> packageName mapping
        Map<String, String> pkgIdToName = new LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            pkgIdToName.put(pkg.getId(), pkg.getName() != null ? pkg.getName() : pkg.getId());
        }

        // Build MPL usage lookup: flowId/flowName -> last execution date
        Map<String, String> flowLastUsed = new LinkedHashMap<>();
        Map<String, String> flowRuntimeStatus = new LinkedHashMap<>();
        if (result.getMessageProcessingLogs() != null) {
            for (MessageProcessingLog mpl : result.getMessageProcessingLogs()) {
                String name = mpl.getIntegrationFlowName();
                if (name == null || name.isBlank()) continue;
                String logEnd = mpl.getLogEnd() != null ? mpl.getLogEnd() : "";
                String existing = flowLastUsed.getOrDefault(name, "");
                if (logEnd.compareTo(existing) > 0) flowLastUsed.put(name, logEnd);
            }
        }
        for (IntegrationFlow flow : result.getAllFlows()) {
            String rt = flow.getRuntimeStatus() != null ? flow.getRuntimeStatus() : "UNKNOWN";
            if (flow.getId() != null) flowRuntimeStatus.put(flow.getId(), rt);
            if (flow.getName() != null) flowRuntimeStatus.put(flow.getName(), rt);
            if (flow.getId() != null && flow.getName() != null) {
                String byId = flowLastUsed.get(flow.getId());
                String byName = flowLastUsed.get(flow.getName());
                String best = "";
                if (byId != null) best = byId;
                if (byName != null && byName.compareTo(best) > 0) best = byName;
                if (!best.isEmpty()) {
                    flowLastUsed.put(flow.getId(), best);
                    flowLastUsed.put(flow.getName(), best);
                }
            }
        }

        // Sheet: Package Dependencies
        {
            Sheet sheet = wb.createSheet("Package Dependencies");
            String[] headers = {"Source Package", "Target Package", "Dependency Types",
                    "# Links", "Cross-Package", "Source Last Used", "Target Last Used",
                    "Link Status", "Flow Links"};
            createHeaderRow(sheet, headerStyle, headers);

            List<PackageDependency> pkgDeps = graph.getPackageDependencies(pkgIdToName);
            int rowNum = 1;
            for (PackageDependency pd : pkgDeps) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(pd.getSourcePackageName());
                row.createCell(1).setCellValue(pd.getTargetPackageName());
                row.createCell(2).setCellValue(pd.getDependencyTypesDisplay());
                row.createCell(3).setCellValue(pd.getStrength());
                row.createCell(4).setCellValue(pd.isCrossPackage() ? "Yes" : "No");

                StringBuilder flowLinks = new StringBuilder();
                String srcLatest = "";
                String tgtLatest = "";
                boolean anySrcActive = false;
                boolean anyTgtActive = false;

                for (Dependency dep : pd.getFlowDependencies()) {
                    if (flowLinks.length() > 0) flowLinks.append("; ");
                    String srcName = dep.getSourceFlowName() != null ? dep.getSourceFlowName() : dep.getSourceFlowId();
                    String tgtName = dep.getTargetFlowName() != null ? dep.getTargetFlowName() : dep.getTargetFlowId();
                    flowLinks.append(srcName).append(" -> ").append(tgtName)
                             .append(" [").append(dep.getType().getDisplayName()).append("]");

                    String srcKey = dep.getSourceFlowId() != null ? dep.getSourceFlowId() : dep.getSourceFlowName();
                    String tgtKey = dep.getTargetFlowId() != null ? dep.getTargetFlowId() : dep.getTargetFlowName();
                    String srcUsed = flowLastUsed.getOrDefault(srcKey, "");
                    String tgtUsed = flowLastUsed.getOrDefault(tgtKey, "");
                    if (srcUsed.compareTo(srcLatest) > 0) srcLatest = srcUsed;
                    if (tgtUsed.compareTo(tgtLatest) > 0) tgtLatest = tgtUsed;
                    if ("STARTED".equalsIgnoreCase(flowRuntimeStatus.getOrDefault(srcKey, ""))) anySrcActive = true;
                    if ("STARTED".equalsIgnoreCase(flowRuntimeStatus.getOrDefault(tgtKey, ""))) anyTgtActive = true;
                }

                String linkStatus;
                boolean srcHasUsage = !srcLatest.isEmpty();
                boolean tgtHasUsage = !tgtLatest.isEmpty();
                if (srcHasUsage && tgtHasUsage) linkStatus = "Active";
                else if (!srcHasUsage && !tgtHasUsage) linkStatus = (anySrcActive || anyTgtActive) ? "Deployed, No Usage" : "Inactive";
                else linkStatus = "Partially Active";

                row.createCell(5).setCellValue(srcLatest.isEmpty() ? "No usage data" : formatCpiDate(srcLatest));
                row.createCell(6).setCellValue(tgtLatest.isEmpty() ? "No usage data" : formatCpiDate(tgtLatest));
                row.createCell(7).setCellValue(linkStatus);
                row.createCell(8).setCellValue(nullSafe(flowLinks.toString()));
            }
            autoSizeColumns(sheet, headers.length);
            if (rowNum > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
            }
        }

        // Sheet: Circular Dependencies
        List<java.util.List<String>> cycles = graph.detectCycles();
        if (!cycles.isEmpty()) {
            Sheet sheet = wb.createSheet("Circular Dependencies");
            String[] headers = {"Cycle #", "Flow Chain"};
            createHeaderRow(sheet, headerStyle, headers);

            int rowNum = 1;
            for (int i = 0; i < cycles.size(); i++) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(i + 1);
                java.util.List<String> cycle = cycles.get(i);
                StringBuilder chain = new StringBuilder();
                for (String flowId : cycle) {
                    if (chain.length() > 0) chain.append(" -> ");
                    IntegrationFlow f = graph.getFlowsById().get(flowId);
                    chain.append(f != null ? f.getName() : flowId);
                }
                row.createCell(1).setCellValue(nullSafe(chain.toString()));
            }
            autoSizeColumns(sheet, headers.length);
        }
    }

    // =========================================================================
    // Credentials / Security Materials Sheet (E4)
    // =========================================================================

    private static final java.util.Set<String> CREDENTIAL_KEYS = java.util.Set.of(
            "credential_name", "credentialname", "credential.name",
            "private.key.alias", "privatekeyalias",
            "public.key.alias", "publickeyalias",
            "certificate.alias", "certificatealias",
            "senderauthcredential", "sender.auth.credential",
            "receiverauthcredential", "receiver.auth.credential",
            "proxyuser", "proxy.user",
            "securitymaterial", "security.material",
            "pgp.secret.keyring.alias", "pgpsecretkeyringalias",
            "pgp.public.keyring.alias", "pgppublickeyringalias"
    );

    private static boolean isCredentialProperty(String key) {
        String lower = key.toLowerCase();
        if (CREDENTIAL_KEYS.contains(lower)) return true;
        return lower.contains("credential") || lower.contains("keystore")
                || lower.contains("certificate") || lower.contains("alias")
                || lower.contains("secret.key") || lower.contains("pgp");
    }

    private static String classifyCredentialType(String propertyKey) {
        String lower = propertyKey.toLowerCase();
        if (lower.contains("oauth")) return "OAuth2";
        if (lower.contains("saml")) return "SAML";
        if (lower.contains("pgp")) return "PGP";
        if (lower.contains("keystore") || lower.contains("key.alias")
                || lower.contains("keyalias") || lower.contains("certificate")) return "Keystore";
        if (lower.contains("credential")) return "Credential";
        return "Security Material";
    }

    private void createCredentialsSheet(Workbook wb, CellStyle headerStyle, ExtractionResult result) {
        Sheet sheet = wb.createSheet("Credentials");
        String[] headers = {
                "Package", "iFlow", "Adapter Type", "Direction",
                "Credential Name", "Credential Type", "Property Key", "Context"
        };
        createHeaderRow(sheet, headerStyle, headers);

        java.util.Map<String, String> flowToPackage = new java.util.LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                String flowName = f.getName() != null ? f.getName() : f.getId();
                flowToPackage.put(flowName, pkg.getName() != null ? pkg.getName() : pkg.getId());
            }
        }

        int rowNum = 1;
        for (IntegrationFlow flow : result.getAllFlows()) {
            if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
            String flowName = flow.getName() != null ? flow.getName() : flow.getId();
            String pkgName = flowToPackage.getOrDefault(flowName, "");

            for (var adapter : flow.getIflowContent().getAdapters()) {
                String adapterType = adapter.getAdapterType() != null ? adapter.getAdapterType() : "";
                String direction = adapter.getDirection() != null ? adapter.getDirection() : "";
                for (var entry : adapter.getProperties().entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                    if (isCredentialProperty(entry.getKey())) {
                        Row row = sheet.createRow(rowNum++);
                        int col = 0;
                        row.createCell(col++).setCellValue(nullSafe(pkgName));
                        row.createCell(col++).setCellValue(nullSafe(flowName));
                        row.createCell(col++).setCellValue(nullSafe(adapterType));
                        row.createCell(col++).setCellValue(nullSafe(direction));
                        row.createCell(col++).setCellValue(nullSafe(entry.getValue()));
                        row.createCell(col++).setCellValue(classifyCredentialType(entry.getKey()));
                        row.createCell(col++).setCellValue(nullSafe(entry.getKey()));
                        row.createCell(col++).setCellValue("Adapter: " + adapterType);
                    }
                }
            }

            for (var entry : flow.getIflowContent().getProcessProperties().entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) continue;
                if (isCredentialProperty(entry.getKey())) {
                    Row row = sheet.createRow(rowNum++);
                    int col = 0;
                    row.createCell(col++).setCellValue(nullSafe(pkgName));
                    row.createCell(col++).setCellValue(nullSafe(flowName));
                    row.createCell(col++).setCellValue("");
                    row.createCell(col++).setCellValue("");
                    row.createCell(col++).setCellValue(nullSafe(entry.getValue()));
                    row.createCell(col++).setCellValue(classifyCredentialType(entry.getKey()));
                    row.createCell(col++).setCellValue(nullSafe(entry.getKey()));
                    row.createCell(col++).setCellValue("Process Property");
                }
            }
        }

        autoSizeColumns(sheet, headers.length);
        if (rowNum > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        }
    }

    // =========================================================================
    // ECC Endpoints Sheet
    // =========================================================================

    private void createEccEndpointsSheet(Workbook wb, CellStyle headerStyle, ExtractionResult result) {
        Sheet sheet = wb.createSheet("ECC Endpoints");
        String[] headers = {
                "Package", "iFlow", "Direction", "Adapter Type", "Transport Protocol",
                "Message Protocol", "Address", "Category"
        };
        createHeaderRow(sheet, headerStyle, headers);

        // Build flow-to-package mapping
        java.util.Map<String, String> flowToPackage = new java.util.LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                String flowName = f.getName() != null ? f.getName() : f.getId();
                flowToPackage.put(flowName, pkg.getName() != null ? pkg.getName() : pkg.getId());
            }
        }

        int rowNum = 1;
        for (IntegrationFlow flow : result.getAllFlows()) {
            if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
            String flowName = flow.getName() != null ? flow.getName() : flow.getId();
            String pkgName = flowToPackage.getOrDefault(flowName, "");

            for (var adapter : flow.getIflowContent().getAdapters()) {
                String type = adapter.getAdapterType() != null ? adapter.getAdapterType() : "";
                String proto = adapter.getTransportProtocol() != null ? adapter.getTransportProtocol() : "";
                String msgProto = adapter.getMessageProtocol() != null ? adapter.getMessageProtocol() : "";
                String category = classifyEndpoint(type, proto, msgProto);

                Row row = sheet.createRow(rowNum++);
                int col = 0;
                row.createCell(col++).setCellValue(nullSafe(pkgName));
                row.createCell(col++).setCellValue(nullSafe(flowName));
                row.createCell(col++).setCellValue(nullSafe(adapter.getDirection()));
                row.createCell(col++).setCellValue(nullSafe(type));
                row.createCell(col++).setCellValue(nullSafe(proto));
                row.createCell(col++).setCellValue(nullSafe(msgProto));
                row.createCell(col++).setCellValue(nullSafe(adapter.getAddress()));
                row.createCell(col++).setCellValue(category);
            }
        }
        autoSizeColumns(sheet, headers.length);
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

    // =========================================================================
    // Flow Chains Sheet
    // =========================================================================

    private void createFlowChainsSheet(Workbook wb, CellStyle headerStyle, ExtractionResult result) {
        Sheet sheet = wb.createSheet("Flow Chains");
        String[] headers = {
                "Chain Type", "Queue / Address", "Sender iFlow", "Sender Package",
                "Sender Last Used", "Sender Runtime",
                "Receiver iFlow", "Receiver Package",
                "Receiver Last Used", "Receiver Runtime"
        };
        createHeaderRow(sheet, headerStyle, headers);

        // Build flow-to-package mapping
        java.util.Map<String, String> flowToPackage = new java.util.LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                flowToPackage.put(f.getId(), pkg.getName() != null ? pkg.getName() : pkg.getId());
            }
        }

        // Build MPL usage and runtime status lookups
        java.util.Map<String, String> flowLastUsed = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> flowRuntimeStatus = new java.util.LinkedHashMap<>();
        if (result.getMessageProcessingLogs() != null) {
            for (MessageProcessingLog mpl : result.getMessageProcessingLogs()) {
                String name = mpl.getIntegrationFlowName();
                if (name == null || name.isBlank()) continue;
                String logEnd = mpl.getLogEnd() != null ? mpl.getLogEnd() : "";
                String existing = flowLastUsed.getOrDefault(name, "");
                if (logEnd.compareTo(existing) > 0) flowLastUsed.put(name, logEnd);
            }
        }
        for (IntegrationFlow flow : result.getAllFlows()) {
            String rt = flow.getRuntimeStatus() != null ? flow.getRuntimeStatus() : "UNKNOWN";
            if (flow.getId() != null) flowRuntimeStatus.put(flow.getId(), rt);
            if (flow.getName() != null) flowRuntimeStatus.put(flow.getName(), rt);
            if (flow.getId() != null && flow.getName() != null) {
                String byId = flowLastUsed.get(flow.getId());
                String byName = flowLastUsed.get(flow.getName());
                String best = "";
                if (byId != null) best = byId;
                if (byName != null && byName.compareTo(best) > 0) best = byName;
                if (!best.isEmpty()) {
                    flowLastUsed.put(flow.getId(), best);
                    flowLastUsed.put(flow.getName(), best);
                }
            }
        }

        // Collect producers and consumers
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

        int rowNum = 1;
        rowNum = writeChainRows(sheet, rowNum, "JMS", jmsProducers, jmsConsumers, flowToPackage, flowLastUsed, flowRuntimeStatus);
        // Orphan JMS consumers
        for (var entry : jmsConsumers.entrySet()) {
            if (!jmsProducers.containsKey(entry.getKey())) {
                for (String[] cons : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue("JMS");
                    row.createCell(1).setCellValue(entry.getKey());
                    row.createCell(2).setCellValue("(no producer found)");
                    row.createCell(3).setCellValue("");
                    row.createCell(4).setCellValue("");
                    row.createCell(5).setCellValue("");
                    row.createCell(6).setCellValue(cons[1]);
                    row.createCell(7).setCellValue(flowToPackage.getOrDefault(cons[0], ""));
                    String consUsed = flowLastUsed.getOrDefault(cons[0], flowLastUsed.getOrDefault(cons[1], ""));
                    row.createCell(8).setCellValue(consUsed.isEmpty() ? "No usage data" : formatCpiDate(consUsed));
                    row.createCell(9).setCellValue(flowRuntimeStatus.getOrDefault(cons[0], flowRuntimeStatus.getOrDefault(cons[1], "UNKNOWN")));
                }
            }
        }
        rowNum = writeChainRows(sheet, rowNum, "ProcessDirect", pdProducers, pdConsumers, flowToPackage, flowLastUsed, flowRuntimeStatus);
        for (var entry : pdConsumers.entrySet()) {
            if (!pdProducers.containsKey(entry.getKey())) {
                for (String[] cons : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue("ProcessDirect");
                    row.createCell(1).setCellValue(entry.getKey());
                    row.createCell(2).setCellValue("(no producer found)");
                    row.createCell(3).setCellValue("");
                    row.createCell(4).setCellValue("");
                    row.createCell(5).setCellValue("");
                    row.createCell(6).setCellValue(cons[1]);
                    row.createCell(7).setCellValue(flowToPackage.getOrDefault(cons[0], ""));
                    String consUsed = flowLastUsed.getOrDefault(cons[0], flowLastUsed.getOrDefault(cons[1], ""));
                    row.createCell(8).setCellValue(consUsed.isEmpty() ? "No usage data" : formatCpiDate(consUsed));
                    row.createCell(9).setCellValue(flowRuntimeStatus.getOrDefault(cons[0], flowRuntimeStatus.getOrDefault(cons[1], "UNKNOWN")));
                }
            }
        }
        autoSizeColumns(sheet, headers.length);
    }

    private int writeChainRows(Sheet sheet, int rowNum, String chainType,
                                java.util.Map<String, java.util.List<String[]>> producers,
                                java.util.Map<String, java.util.List<String[]>> consumers,
                                java.util.Map<String, String> flowToPackage,
                                java.util.Map<String, String> flowLastUsed,
                                java.util.Map<String, String> flowRuntimeStatus) {
        for (var entry : producers.entrySet()) {
            String queue = entry.getKey();
            java.util.List<String[]> cons = consumers.getOrDefault(queue, java.util.List.of());
            if (cons.isEmpty()) {
                for (String[] prod : entry.getValue()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(chainType);
                    row.createCell(1).setCellValue(queue);
                    row.createCell(2).setCellValue(prod[1]);
                    row.createCell(3).setCellValue(flowToPackage.getOrDefault(prod[0], ""));
                    String prodUsed = flowLastUsed.getOrDefault(prod[0], flowLastUsed.getOrDefault(prod[1], ""));
                    row.createCell(4).setCellValue(prodUsed.isEmpty() ? "No usage data" : formatCpiDate(prodUsed));
                    row.createCell(5).setCellValue(flowRuntimeStatus.getOrDefault(prod[0], flowRuntimeStatus.getOrDefault(prod[1], "UNKNOWN")));
                    row.createCell(6).setCellValue("(no consumer found)");
                    row.createCell(7).setCellValue("");
                    row.createCell(8).setCellValue("");
                    row.createCell(9).setCellValue("");
                }
            } else {
                for (String[] prod : entry.getValue()) {
                    for (String[] con : cons) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(chainType);
                        row.createCell(1).setCellValue(queue);
                        row.createCell(2).setCellValue(prod[1]);
                        row.createCell(3).setCellValue(flowToPackage.getOrDefault(prod[0], ""));
                        String prodUsed = flowLastUsed.getOrDefault(prod[0], flowLastUsed.getOrDefault(prod[1], ""));
                        row.createCell(4).setCellValue(prodUsed.isEmpty() ? "No usage data" : formatCpiDate(prodUsed));
                        row.createCell(5).setCellValue(flowRuntimeStatus.getOrDefault(prod[0], flowRuntimeStatus.getOrDefault(prod[1], "UNKNOWN")));
                        row.createCell(6).setCellValue(con[1]);
                        row.createCell(7).setCellValue(flowToPackage.getOrDefault(con[0], ""));
                        String conUsed = flowLastUsed.getOrDefault(con[0], flowLastUsed.getOrDefault(con[1], ""));
                        row.createCell(8).setCellValue(conUsed.isEmpty() ? "No usage data" : formatCpiDate(conUsed));
                        row.createCell(9).setCellValue(flowRuntimeStatus.getOrDefault(con[0], flowRuntimeStatus.getOrDefault(con[1], "UNKNOWN")));
                    }
                }
            }
        }
        return rowNum;
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
