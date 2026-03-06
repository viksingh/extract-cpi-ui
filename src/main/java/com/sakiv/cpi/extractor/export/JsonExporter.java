package com.sakiv.cpi.extractor.export;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sakiv.cpi.extractor.model.ExtractionResult;
import com.sakiv.cpi.extractor.util.DateFilterUtil;
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

        // Convert /Date(...)/ and plain epoch strings to human-readable format during serialization
        SimpleModule dateModule = new SimpleModule();
        dateModule.addSerializer(String.class, new JsonSerializer<>() {
            @Override
            public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(DateFilterUtil.formatCpiDate(value));
            }
        });
        mapper.registerModule(dateModule);

        mapper.writeValue(filePath.toFile(), result);

        log.info("JSON report exported to: {}", filePath.toAbsolutePath());
        return filePath.toAbsolutePath().toString();
    }

}
