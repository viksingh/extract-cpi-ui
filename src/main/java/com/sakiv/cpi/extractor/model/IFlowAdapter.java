package com.sakiv.cpi.extractor.model;

import java.util.LinkedHashMap;
import java.util.Map;

// @author Vikas Singh | Created: 2026-02-22
public class IFlowAdapter {

    private String id;
    private String name;
    private String adapterType;
    private String direction;
    private String transportProtocol;
    private String messageProtocol;
    private String address;
    private Map<String, String> properties = new LinkedHashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAdapterType() { return adapterType; }
    public void setAdapterType(String adapterType) { this.adapterType = adapterType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getTransportProtocol() { return transportProtocol; }
    public void setTransportProtocol(String transportProtocol) { this.transportProtocol = transportProtocol; }

    public String getMessageProtocol() { return messageProtocol; }
    public void setMessageProtocol(String messageProtocol) { this.messageProtocol = messageProtocol; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    @Override
    public String toString() {
        return String.format("IFlowAdapter[id=%s, type=%s, direction=%s]", id, adapterType, direction);
    }
}
