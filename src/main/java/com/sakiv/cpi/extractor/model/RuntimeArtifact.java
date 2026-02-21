package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a deployed runtime artifact in SAP CPI.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuntimeArtifact {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Version")
    private String version;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("DeployedBy")
    private String deployedBy;

    @JsonProperty("DeployedOn")
    private String deployedOn;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("ErrorInformation")
    private String errorInformation;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDeployedBy() { return deployedBy; }
    public void setDeployedBy(String deployedBy) { this.deployedBy = deployedBy; }

    public String getDeployedOn() { return deployedOn; }
    public void setDeployedOn(String deployedOn) { this.deployedOn = deployedOn; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorInformation() { return errorInformation; }
    public void setErrorInformation(String errorInformation) { this.errorInformation = errorInformation; }

    @Override
    public String toString() {
        return String.format("RuntimeArtifact[id=%s, name=%s, status=%s, type=%s]", id, name, status, type);
    }
}
