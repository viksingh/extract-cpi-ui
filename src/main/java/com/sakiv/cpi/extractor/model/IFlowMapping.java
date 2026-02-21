package com.sakiv.cpi.extractor.model;

import java.util.LinkedHashMap;
import java.util.Map;

// @author Vikas Singh | Created: 2026-02-22
public class IFlowMapping {

    private String id;
    private String name;
    private String mappingType;
    private String resourceId;
    private Map<String, String> properties = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMappingType() { return mappingType; }
    public void setMappingType(String mappingType) { this.mappingType = mappingType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    @Override
    public String toString() {
        return String.format("IFlowMapping[id=%s, type=%s, name=%s]", id, mappingType, name);
    }
}
