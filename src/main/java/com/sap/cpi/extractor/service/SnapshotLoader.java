package com.sap.cpi.extractor.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sap.cpi.extractor.model.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class SnapshotLoader {

    private static final Logger log = LoggerFactory.getLogger(SnapshotLoader.class);

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ExtractionResult load(File jsonFile) throws IOException {
        log.info("Loading snapshot from: {}", jsonFile.getAbsolutePath());
        ExtractionResult result = mapper.readValue(jsonFile, ExtractionResult.class);
        log.info("Snapshot loaded: tenant={}, extractedAt={}, packages={}, flows={}",
                result.getTenantUrl(), result.getExtractedAt(),
                result.getPackages().size(), result.getAllFlows().size());
        return result;
    }
}
