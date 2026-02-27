
package com.sakiv.cpi.extractor.export;

import com.sakiv.cpi.extractor.model.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

    /**
     * Format SAP CPI OData date strings like "/Date(1234567890000+0530)/" to human-readable.
     * Correctly strips the optional timezone offset before parsing the epoch ms.
     */
    // @author Vikas Singh | Created: 2025-12-14
    private String formatCpiDate(String cpiDate) {
        if (cpiDate == null || cpiDate.isBlank()) return "";
        try {
            if (cpiDate.contains("/Date(")) {
                int start = cpiDate.indexOf('(') + 1;
                int end = cpiDate.indexOf(')', start);
                if (end < 0) end = cpiDate.length();
                String inner = cpiDate.substring(start, end);
                int tzPlus = inner.indexOf('+');
                int tzMinus = inner.lastIndexOf('-');
                if (tzMinus == 0) tzMinus = -1;
                int tzSep = tzPlus >= 0 ? tzPlus : tzMinus;
                String epochPart = (tzSep > 0) ? inner.substring(0, tzSep) : inner;
                long epoch = Long.parseLong(epochPart.trim());
                java.time.Instant instant = java.time.Instant.ofEpochMilli(epoch);
                return java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return cpiDate;
        } catch (Exception e) {
            return cpiDate;
        }
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
}
