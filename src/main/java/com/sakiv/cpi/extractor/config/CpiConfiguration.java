package com.sakiv.cpi.extractor.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads and manages application configuration from properties files
 * and environment variables.
 *
 * Configuration loading order:
 *   1. Built-in defaults (non-sensitive: API paths, export settings, HTTP settings)
 *   2. Classpath application.properties (non-sensitive defaults bundled in JAR)
 *   3. External config file (credentials, URLs - passed as CLI argument)
 *   4. Environment variables (highest priority, override everything)
 *
 * Credentials and tenant URL must be supplied externally via either:
 *   - An external config file: java -jar extractor.jar config.properties
 *   - Environment variables: CPI_BASE_URL, CPI_OAUTH_CLIENT_ID, etc.
 */
public class CpiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CpiConfiguration.class);
    private final Properties properties;
    private String configSource = "defaults only";

    // @author Vikas Singh | Created: 2025-11-01
    public CpiConfiguration() {
        this.properties = new Properties();
        loadDefaults();
        loadFromClasspath();
        overrideFromEnvironment();
    }

    // @author Vikas Singh | Created: 2025-11-01
    public CpiConfiguration(String configFilePath) {
        this.properties = new Properties();
        loadDefaults();
        loadFromClasspath();
        loadFromFile(configFilePath);
        this.configSource = configFilePath;
        overrideFromEnvironment();
    }

    // @author Vikas Singh | Created: 2025-11-01
    private void loadDefaults() {
        properties.setProperty("http.connect.timeout.ms", "30000");
        properties.setProperty("http.read.timeout.ms", "60000");
        properties.setProperty("http.max.retries", "3");
        properties.setProperty("http.retry.delay.ms", "2000");
        properties.setProperty("export.format", "xlsx");
        properties.setProperty("export.output.dir", "./output");
        properties.setProperty("export.filename.prefix", "cpi_artifacts");
        properties.setProperty("extract.packages", "true");
        properties.setProperty("extract.flows", "true");
        properties.setProperty("extract.valuemappings", "true");
        properties.setProperty("extract.configurations", "true");
        properties.setProperty("extract.runtime.status", "true");
    }

    // @author Vikas Singh | Created: 2025-11-01
    private void loadFromClasspath() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (is != null) {
                properties.load(is);
                log.info("Loaded configuration from classpath");
            }
        } catch (IOException e) {
            log.warn("Could not load application.properties from classpath: {}", e.getMessage());
        }
    }

    // @author Vikas Singh | Created: 2025-11-01
    private void loadFromFile(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Config file not found: " + filePath);
        }
        try (InputStream is = new FileInputStream(filePath)) {
            properties.load(is);
            log.info("Loaded configuration from: {}", filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load config from " + filePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Environment variables override properties.
     * Convention: CPI_BASE_URL maps to cpi.base.url
     */
    // @author Vikas Singh | Created: 2025-11-02
    private void overrideFromEnvironment() {
        mapEnvToProperty("CPI_BASE_URL", "cpi.base.url");
        mapEnvToProperty("CPI_AUTH_TYPE", "cpi.auth.type");
        mapEnvToProperty("CPI_OAUTH_TOKEN_URL", "cpi.oauth.token.url");
        mapEnvToProperty("CPI_OAUTH_CLIENT_ID", "cpi.oauth.client.id");
        mapEnvToProperty("CPI_OAUTH_CLIENT_SECRET", "cpi.oauth.client.secret");
        mapEnvToProperty("CPI_BASIC_USERNAME", "cpi.basic.username");
        mapEnvToProperty("CPI_BASIC_PASSWORD", "cpi.basic.password");
    }

    // @author Vikas Singh | Created: 2025-11-02
    private void mapEnvToProperty(String envVar, String propKey) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            properties.setProperty(propKey, value);
            log.debug("Override from env: {} -> {}", envVar, propKey);
        }
    }

    // --- Getters ---

    // @author Vikas Singh | Created: 2025-11-02
    public String get(String key) {
        return properties.getProperty(key);
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    // @author Vikas Singh | Created: 2025-11-02
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // @author Vikas Singh | Created: 2025-11-02
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = properties.getProperty(key);
        return val != null ? Boolean.parseBoolean(val) : defaultValue;
    }

    // --- Convenience Accessors ---

    // @author Vikas Singh | Created: 2025-11-02
    public String getBaseUrl() {
        return get("cpi.base.url");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getAuthType() {
        return get("cpi.auth.type", "oauth2");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getOAuthTokenUrl() {
        return get("cpi.oauth.token.url");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getOAuthClientId() {
        return get("cpi.oauth.client.id");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getOAuthClientSecret() {
        return get("cpi.oauth.client.secret");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getBasicUsername() {
        return get("cpi.basic.username");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getBasicPassword() {
        return get("cpi.basic.password");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getExportFormat() {
        return get("export.format", "xlsx");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getOutputDir() {
        return get("export.output.dir", "./output");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getFilenamePrefix() {
        return get("export.filename.prefix", "cpi_artifacts");
    }

    // @author Vikas Singh | Created: 2025-11-02
    public String getConfigSource() {
        return configSource;
    }

    // @author Vikas Singh | Created: 2025-11-02
    public void validate() {
        String hint = " — provide it in an external config file or via environment variables."
                + " See config.properties.template for reference.";

        if (getBaseUrl() == null || getBaseUrl().isBlank()) {
            throw new IllegalStateException("cpi.base.url must be configured" + hint);
        }
        if ("oauth2".equals(getAuthType())) {
            requireNonBlank("cpi.oauth.token.url", "OAuth token URL", hint);
            requireNonBlank("cpi.oauth.client.id", "OAuth client ID", hint);
            requireNonBlank("cpi.oauth.client.secret", "OAuth client secret", hint);
        } else if ("basic".equals(getAuthType())) {
            requireNonBlank("cpi.basic.username", "Basic auth username", hint);
            requireNonBlank("cpi.basic.password", "Basic auth password", hint);
        }
    }

    // @author Vikas Singh | Created: 2025-11-02
    private void requireNonBlank(String key, String label, String hint) {
        if (get(key) == null || get(key).isBlank()) {
            throw new IllegalStateException(label + " (" + key + ") must be configured" + hint);
        }
    }
}
