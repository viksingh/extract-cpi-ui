package com.sakiv.cpi.extractor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Stores a named connection profile for quick tenant switching (DEV/QA/PROD).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConnectionProfile {

    private String name;
    private String tenantUrl;
    private String authType;
    private String oauthTokenUrl;
    private String oauthClientId;
    private String oauthClientSecret;
    private String basicUsername;
    private String basicPassword;
    private String outputDir;
    private String filenamePrefix;

    public ConnectionProfile() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTenantUrl() { return tenantUrl; }
    public void setTenantUrl(String tenantUrl) { this.tenantUrl = tenantUrl; }

    public String getAuthType() { return authType; }
    public void setAuthType(String authType) { this.authType = authType; }

    public String getOauthTokenUrl() { return oauthTokenUrl; }
    public void setOauthTokenUrl(String oauthTokenUrl) { this.oauthTokenUrl = oauthTokenUrl; }

    public String getOauthClientId() { return oauthClientId; }
    public void setOauthClientId(String oauthClientId) { this.oauthClientId = oauthClientId; }

    public String getOauthClientSecret() { return oauthClientSecret; }
    public void setOauthClientSecret(String oauthClientSecret) { this.oauthClientSecret = oauthClientSecret; }

    public String getBasicUsername() { return basicUsername; }
    public void setBasicUsername(String basicUsername) { this.basicUsername = basicUsername; }

    public String getBasicPassword() { return basicPassword; }
    public void setBasicPassword(String basicPassword) { this.basicPassword = basicPassword; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public String getFilenamePrefix() { return filenamePrefix; }
    public void setFilenamePrefix(String filenamePrefix) { this.filenamePrefix = filenamePrefix; }

    @Override
    public String toString() {
        return name != null ? name : "(unnamed)";
    }
}
