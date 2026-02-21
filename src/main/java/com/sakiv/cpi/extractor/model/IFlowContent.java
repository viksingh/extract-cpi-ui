package com.sakiv.cpi.extractor.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// @author Vikas Singh | Created: 2026-02-22
public class IFlowContent {

    private String flowId;
    private String version;
    private String rawXml;

    private List<IFlowRoute> routes = new ArrayList<>();
    private List<IFlowAdapter> adapters = new ArrayList<>();
    private List<IFlowMapping> mappings = new ArrayList<>();
    private List<IFlowEndpoint> endpoints = new ArrayList<>();
    private Map<String, String> processProperties = new LinkedHashMap<>();

    private List<ScriptInfo> scripts = new ArrayList<>();
    private List<String> mappingFiles = new ArrayList<>();

    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getRawXml() { return rawXml; }
    public void setRawXml(String rawXml) { this.rawXml = rawXml; }

    public List<IFlowRoute> getRoutes() { return routes; }
    public void setRoutes(List<IFlowRoute> routes) { this.routes = routes; }

    public List<IFlowAdapter> getAdapters() { return adapters; }
    public void setAdapters(List<IFlowAdapter> adapters) { this.adapters = adapters; }

    public List<IFlowMapping> getMappings() { return mappings; }
    public void setMappings(List<IFlowMapping> mappings) { this.mappings = mappings; }

    public List<IFlowEndpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<IFlowEndpoint> endpoints) { this.endpoints = endpoints; }

    public Map<String, String> getProcessProperties() { return processProperties; }
    public void setProcessProperties(Map<String, String> processProperties) { this.processProperties = processProperties; }

    public List<ScriptInfo> getScripts() { return scripts; }
    public void setScripts(List<ScriptInfo> scripts) { this.scripts = scripts; }

    public List<String> getMappingFiles() { return mappingFiles; }
    public void setMappingFiles(List<String> mappingFiles) { this.mappingFiles = mappingFiles; }

    @Override
    public String toString() {
        return String.format("IFlowContent[id=%s, ver=%s, routes=%d, adapters=%d, mappings=%d, endpoints=%d, scripts=%d]",
                flowId, version, routes.size(), adapters.size(), mappings.size(), endpoints.size(), scripts.size());
    }
}
