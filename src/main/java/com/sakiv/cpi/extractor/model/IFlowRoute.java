package com.sakiv.cpi.extractor.model;

import java.util.LinkedHashMap;
import java.util.Map;

// @author Vikas Singh | Created: 2026-02-22
public class IFlowRoute {

    private String id;
    private String name;
    private String type;
    private String activityType;
    private String componentType;
    private String sourceRef;
    private String targetRef;
    private String condition;
    private Map<String, String> properties = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getActivityType() { return activityType; }
    public void setActivityType(String activityType) { this.activityType = activityType; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    @Override
    public String toString() {
        return String.format("IFlowRoute[id=%s, type=%s, name=%s]", id, type, name);
    }
}
