package com.sap.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Container holding all extracted CPI artifact data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractionResult {

    private LocalDateTime extractedAt;
    private String tenantUrl;

    private List<IntegrationPackage> packages = new ArrayList<>();
    private List<IntegrationFlow> allFlows = new ArrayList<>();
    private List<ValueMapping> allValueMappings = new ArrayList<>();
    private List<RuntimeArtifact> runtimeArtifacts = new ArrayList<>();

    private int totalPackages;
    private int totalFlows;
    private int totalValueMappings;
    private int deployedArtifacts;
    private int errorArtifacts;

    /** No-arg constructor for Jackson deserialization. */
    public ExtractionResult() {
        this.extractedAt = LocalDateTime.now();
    }

    public ExtractionResult(String tenantUrl) {
        this.extractedAt = LocalDateTime.now();
        this.tenantUrl = tenantUrl;
    }

    public void computeSummary() {
        totalPackages = packages.size();
        totalFlows = allFlows.size();
        totalValueMappings = allValueMappings.size();
        deployedArtifacts = (int) runtimeArtifacts.stream()
                .filter(r -> "STARTED".equalsIgnoreCase(r.getStatus()))
                .count();
        errorArtifacts = (int) runtimeArtifacts.stream()
                .filter(r -> "ERROR".equalsIgnoreCase(r.getStatus()))
                .count();
    }

    public String getSummary() {
        computeSummary();
        return String.format("""
                ================================================
                SAP CPI Artifact Extraction Summary
                ================================================
                Tenant:              %s
                Extracted At:        %s
                ------------------------------------------------
                Integration Packages: %d
                Integration Flows:    %d
                Value Mappings:       %d
                Runtime Artifacts:    %d
                  - STARTED:          %d
                  - ERROR:            %d
                ================================================
                """,
                tenantUrl, extractedAt,
                totalPackages, totalFlows, totalValueMappings,
                runtimeArtifacts.size(), deployedArtifacts, errorArtifacts);
    }

    // Getters and Setters

    public LocalDateTime getExtractedAt() { return extractedAt; }
    public void setExtractedAt(LocalDateTime extractedAt) { this.extractedAt = extractedAt; }

    public String getTenantUrl() { return tenantUrl; }
    public void setTenantUrl(String tenantUrl) { this.tenantUrl = tenantUrl; }

    public List<IntegrationPackage> getPackages() { return packages; }
    public void setPackages(List<IntegrationPackage> packages) { this.packages = packages; }

    public List<IntegrationFlow> getAllFlows() { return allFlows; }
    public void setAllFlows(List<IntegrationFlow> allFlows) { this.allFlows = allFlows; }

    public List<ValueMapping> getAllValueMappings() { return allValueMappings; }
    public void setAllValueMappings(List<ValueMapping> allValueMappings) { this.allValueMappings = allValueMappings; }

    public List<RuntimeArtifact> getRuntimeArtifacts() { return runtimeArtifacts; }
    public void setRuntimeArtifacts(List<RuntimeArtifact> runtimeArtifacts) { this.runtimeArtifacts = runtimeArtifacts; }
}
