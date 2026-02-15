package com.sap.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an externalized configuration parameter of an iFlow.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {

    @JsonProperty("ParameterKey")
    private String parameterKey;

    @JsonProperty("ParameterValue")
    private String parameterValue;

    @JsonProperty("DataType")
    private String dataType;

    // Parent artifact reference
    private String artifactId;

    public String getParameterKey() { return parameterKey; }
    public void setParameterKey(String parameterKey) { this.parameterKey = parameterKey; }

    public String getParameterValue() { return parameterValue; }
    public void setParameterValue(String parameterValue) { this.parameterValue = parameterValue; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    @Override
    public String toString() {
        return String.format("Configuration[key=%s, value=%s]", parameterKey, parameterValue);
    }
}
