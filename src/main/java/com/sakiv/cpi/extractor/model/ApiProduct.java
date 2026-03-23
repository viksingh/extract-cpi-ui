package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an SAP API Management API Product.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiProduct {

    @JsonProperty("name")
    private String name;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    private String version;

    @JsonProperty("isPublished")
    private boolean published;

    @JsonProperty("quota_count")
    private int quotaCount;

    @JsonProperty("quota_interval")
    private int quotaInterval;

    @JsonProperty("quota_time_unit")
    private String quotaTimeUnit;

    @JsonProperty("scope")
    private String scope;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("changed_by")
    private String modifiedBy;

    @JsonProperty("changed_at")
    private String modifiedAt;

    // Proxy names belonging to this product — populated from navigation property
    private List<String> apiProxies = new ArrayList<>();

    public ApiProduct() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public int getQuotaCount() { return quotaCount; }
    public void setQuotaCount(int quotaCount) { this.quotaCount = quotaCount; }

    public int getQuotaInterval() { return quotaInterval; }
    public void setQuotaInterval(int quotaInterval) { this.quotaInterval = quotaInterval; }

    public String getQuotaTimeUnit() { return quotaTimeUnit; }
    public void setQuotaTimeUnit(String quotaTimeUnit) { this.quotaTimeUnit = quotaTimeUnit; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public String getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }

    public List<String> getApiProxies() { return apiProxies; }
    public void setApiProxies(List<String> apiProxies) { this.apiProxies = apiProxies; }
}
