package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an SAP CPI Value Mapping design-time artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueMapping {

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

    @JsonProperty("CreatedBy")
    private String createdBy;

    @JsonProperty("CreatedAt")
    private String createdAt;

    @JsonProperty("ModifiedBy")
    private String modifiedBy;

    @JsonProperty("ModifiedAt")
    private String modifiedAt;

    // Runtime info
    private String runtimeStatus;

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

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public String getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getRuntimeStatus() { return runtimeStatus; }
    public void setRuntimeStatus(String runtimeStatus) { this.runtimeStatus = runtimeStatus; }

    @Override
    public String toString() {
        return String.format("ValueMapping[id=%s, name=%s]", id, name);
    }
}
