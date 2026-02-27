package com.sakiv.cpi.extractor.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sakiv.cpi.extractor.model.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;

/**
 * Exports CPI extraction results to a JSON file.
 */
public class JsonExporter {

    private static final Logger log = LoggerFactory.getLogger(JsonExporter.class);

    // @author Vikas Singh | Created: 2025-12-07
    public String export(ExtractionResult result, String outputDir, String filenamePrefix)
            throws IOException {

        Path outputPath = Path.of(outputDir);
        Files.createDirectories(outputPath);

        String timestamp = result.getExtractedAt()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = filenamePrefix + "_" + timestamp + ".json";
        Path filePath = outputPath.resolve(filename);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mapper.writeValue(filePath.toFile(), result);

        log.info("JSON report exported to: {}", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }
}
