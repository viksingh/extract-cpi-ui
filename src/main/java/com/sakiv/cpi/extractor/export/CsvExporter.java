package com.sakiv.cpi.extractor.export;

import com.sakiv.cpi.extractor.model.*;
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
                        pkg.getVendor(), pkg.getMode(), pkg.getCreatedBy(), pkg.getCreationDate(),
                        pkg.getModifiedBy(), pkg.getModifiedDate(), pkg.getProducts(),
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
                        flow.getCreatedBy(), flow.getCreatedAt(), flow.getModifiedBy(),
                        flow.getModifiedAt(), flow.getRuntimeStatus(), flow.getDeployedVersion(),
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
                        vm.getVersion(), vm.getCreatedBy(), vm.getCreatedAt(),
                        vm.getModifiedBy(), vm.getModifiedAt());
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
                        rt.getStatus(), rt.getDeployedBy(), rt.getDeployedOn(),
                        rt.getErrorInformation());
            }
        }
    }
}
