package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an SAP CPI Integration Package.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntegrationPackage {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("ShortText")
    private String shortText;

    @JsonProperty("Version")
    private String version;

    @JsonProperty("Vendor")
    private String vendor;

    @JsonProperty("Mode")
    private String mode;

    @JsonProperty("SupportedPlatform")
    private String supportedPlatform;

    @JsonProperty("ModifiedBy")
    private String modifiedBy;

    @JsonProperty("CreationDate")
    private String creationDate;

    @JsonProperty("ModifiedDate")
    private String modifiedDate;

    @JsonProperty("CreatedBy")
    private String createdBy;

    @JsonProperty("Products")
    private String products;

    @JsonProperty("Keywords")
    private String keywords;

    @JsonProperty("Countries")
    private String countries;

    @JsonProperty("Industries")
    private String industries;

    @JsonProperty("LineOfBusiness")
    private String lineOfBusiness;

    @JsonProperty("ResourceId")
    private String resourceId;

    private List<IntegrationFlow> integrationFlows = new ArrayList<>();
    private List<ValueMapping> valueMappings = new ArrayList<>();

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getShortText() { return shortText; }
    public void setShortText(String shortText) { this.shortText = shortText; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getSupportedPlatform() { return supportedPlatform; }
    public void setSupportedPlatform(String supportedPlatform) { this.supportedPlatform = supportedPlatform; }

    public String getModifiedBy() { return modifiedBy; }
    public void setModifiedBy(String modifiedBy) { this.modifiedBy = modifiedBy; }

    public String getCreationDate() { return creationDate; }
    public void setCreationDate(String creationDate) { this.creationDate = creationDate; }

    public String getModifiedDate() { return modifiedDate; }
    public void setModifiedDate(String modifiedDate) { this.modifiedDate = modifiedDate; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getProducts() { return products; }
    public void setProducts(String products) { this.products = products; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public String getCountries() { return countries; }
    public void setCountries(String countries) { this.countries = countries; }

    public String getIndustries() { return industries; }
    public void setIndustries(String industries) { this.industries = industries; }

    public String getLineOfBusiness() { return lineOfBusiness; }
    public void setLineOfBusiness(String lineOfBusiness) { this.lineOfBusiness = lineOfBusiness; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public List<IntegrationFlow> getIntegrationFlows() { return integrationFlows; }
    public void setIntegrationFlows(List<IntegrationFlow> flows) { this.integrationFlows = flows; }

    public List<ValueMapping> getValueMappings() { return valueMappings; }
    public void setValueMappings(List<ValueMapping> valueMappings) { this.valueMappings = valueMappings; }

    @Override
    public String toString() {
        return String.format("IntegrationPackage[id=%s, name=%s, version=%s]", id, name, version);
    }
}
