package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an SAP API Management API Proxy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiProxy {

    @JsonProperty("name")
    private String name;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("version")
    private String version;

    @JsonProperty("base_path")
    private String basePath;

    @JsonProperty("virtual_host")
    private String virtualHost;

    @JsonProperty("state")
    private String state;

    @JsonProperty("service_code")
    private String serviceCode;

    @JsonProperty("isPublished")
    private boolean published;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("changed_by")
    private String modifiedBy;

    @JsonProperty("changed_at")
    private String modifiedAt;

    // Target endpoint URL — populated from expanded targetEndPoints or provider
    private String targetUrl;

    // Product membership — populated during correlation
    private List<String> apiProducts = new ArrayList<>();

    public ApiProxy() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public String getVirtualHost() { return virtualHost; }
    public void setVirtualHost(String virtualHost) { this.virtualHost = virtualHost; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public String getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }

    public String getTargetUrl() { return targetUrl; }
    public void setTargetUrl(String targetUrl) { this.targetUrl = targetUrl; }

    public List<String> getApiProducts() { return apiProducts; }
    public void setApiProducts(List<String> apiProducts) { this.apiProducts = apiProducts; }
}
