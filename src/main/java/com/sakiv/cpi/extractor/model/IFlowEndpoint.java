package com.sakiv.cpi.extractor.model;

// @author Vikas Singh | Created: 2026-02-14
public class IFlowEndpoint {

    private String id;
    private String name;
    private String type;
    private String componentType;
    private String address;
    private String role;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getComponentType() { return componentType; }
    public void setComponentType(String componentType) { this.componentType = componentType; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    @Override
    public String toString() {
        return String.format("IFlowEndpoint[id=%s, name=%s, role=%s]", id, name, role);
    }
}
