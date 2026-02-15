package com.sap.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an SAP CPI Integration Flow (iFlow) design-time artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationFlow {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Version")
    private String version;

    @JsonProperty("PackageId")
    private String packageId;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Sender")
    private String sender;

    @JsonProperty("Receiver")
    private String receiver;

    @JsonProperty("CreatedBy")
    private String createdBy;

    @JsonProperty("CreatedAt")
    private String createdAt;

    @JsonProperty("ModifiedBy")
    private String modifiedBy;

    @JsonProperty("ModifiedAt")
    private String modifiedAt;

    @JsonProperty("ArtifactContent")
    private String artifactContent;

    // Runtime info (populated separately)
    private String runtimeStatus;
    private String deployedVersion;
    private String deployedBy;
    private String deployedAt;
    private String runtimeError;

    // Configurations (populated separately)
    private List<Configuration> configurations = new ArrayList<>();

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public String getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getArtifactContent() { return artifactContent; }
    public void setArtifactContent(String artifactContent) { this.artifactContent = artifactContent; }

    public String getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }

    public String getDeployedVersion() { return deployedVersion; }
    public void setDeployedVersion(String deployedVersion) { this.deployedVersion = deployedVersion; }

    public String getDeployedBy() { return deployedBy; }
    public void setDeployedBy(String deployedBy) { this.deployedBy = deployedBy; }

    public String getDeployedAt() { return deployedAt; }
    public void setDeployedAt(String deployedAt) { this.deployedAt = deployedAt; }

    public String getRuntimeError() { return runtimeError; }
    public void setRuntimeError(String runtimeError) { this.runtimeError = runtimeError; }

    public List<Configuration> getConfigurations() { return configurations; }
    public void setConfigurations(List<Configuration> configurations) { this.configurations = configurations; }

    @Override
    public String toString() {
        return String.format("IntegrationFlow[id=%s, name=%s, version=%s, status=%s]",
                id, name, version, runtimeStatus);
    }
}
