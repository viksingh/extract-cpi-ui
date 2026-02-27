package com.sakiv.cpi.extractor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakiv.cpi.extractor.config.CpiConfiguration;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * HTTP client for SAP CPI OData API calls.
 * Supports OAuth2 Client Credentials and Basic Authentication.
 */
public class CpiHttpClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(CpiHttpClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final CpiConfiguration config;
    private final CloseableHttpClient httpClient;
    private final int maxRetries;
    private final long retryDelayMs;

    // OAuth2 token cache
    private String accessToken;
    private Instant tokenExpiry;

    // @author Vikas Singh | Created: 2025-11-29
    public CpiHttpClient(CpiConfiguration config) {
        this.config = config;
        this.maxRetries = config.getInt("http.max.retries", 3);
        this.retryDelayMs = config.getInt("http.retry.delay.ms", 2000);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(config.getInt("http.connect.timeout.ms", 30000))
                .setSocketTimeout(config.getInt("http.read.timeout.ms", 60000))
                .build();

        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * Execute GET request against CPI OData API.
     *
     * @param urlOrPath Either a relative API path or a full URL (for pagination)
     * @return JSON response as string
     */
    // @author Vikas Singh | Created: 2025-11-29
    public String get(String urlOrPath) throws IOException {
        String fullUrl;
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            fullUrl = urlOrPath;
        } else {
            fullUrl = config.getBaseUrl() + urlOrPath;
        }
        log.debug("GET {}", fullUrl);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpGet request = new HttpGet(fullUrl);
                request.setHeader(HttpHeaders.ACCEPT, "application/json");
                request.setHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

                    if (statusCode == 200) {
                        return body;
                    } else if (statusCode == 401 && attempt < maxRetries) {
                        log.warn("Got 401, refreshing token and retrying (attempt {}/{})", attempt, maxRetries);
                        invalidateToken();
                        continue;
                    } else if (statusCode == 429) {
                        log.warn("Rate limited (429), waiting before retry (attempt {}/{})", attempt, maxRetries);
                        Thread.sleep(retryDelayMs * attempt);
                        continue;
                    } else if (statusCode >= 500 && attempt < maxRetries) {
                        log.warn("Server error {}, retrying (attempt {}/{})", statusCode, attempt, maxRetries);
                        Thread.sleep(retryDelayMs);
                        continue;
                    } else {
                        throw new IOException(String.format(
                                "HTTP %d from %s: %s", statusCode, urlOrPath, body));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }
        throw new IOException("Max retries exceeded for: " + urlOrPath);
    }

    /**
     * Get the authorization header based on the configured auth type.
     */
    // @author Vikas Singh | Created: 2025-11-30
    private String getAuthHeader() throws IOException {
        return switch (config.getAuthType().toLowerCase()) {
            case "oauth2" -> "Bearer " + getOAuth2Token();
            case "basic" -> "Basic " + Base64.getEncoder().encodeToString(
                    (config.getBasicUsername() + ":" + config.getBasicPassword())
                            .getBytes(StandardCharsets.UTF_8));
            default -> throw new IllegalStateException("Unknown auth type: " + config.getAuthType());
        };
    }

    /**
     * Obtain or reuse an OAuth2 access token via Client Credentials grant.
     */
    // @author Vikas Singh | Created: 2025-11-30
    private synchronized String getOAuth2Token() throws IOException {
        if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return accessToken;
        }

        log.info("Requesting new OAuth2 access token from {}", config.getOAuthTokenUrl());

        HttpPost tokenRequest = new HttpPost(config.getOAuthTokenUrl());
        tokenRequest.setHeader(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

        // Client credentials in Authorization header
        String credentials = config.getOAuthClientId() + ":" + config.getOAuthClientSecret();
        tokenRequest.setHeader(HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

        tokenRequest.setEntity(new StringEntity("grant_type=client_credentials"));

        try (CloseableHttpResponse response = httpClient.execute(tokenRequest)) {
            int statusCode = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            if (statusCode != 200) {
                throw new IOException("OAuth2 token request failed with HTTP " + statusCode + ": " + body);
            }

            JsonNode json = mapper.readTree(body);
            accessToken = json.get("access_token").asText();
            int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 3600;
            // Refresh 60 seconds before actual expiry
            tokenExpiry = Instant.now().plusSeconds(expiresIn - 60);

            log.info("OAuth2 token obtained, expires in {} seconds", expiresIn);
            return accessToken;
        }
    }

    // @author Vikas Singh | Created: 2025-11-30
    private void invalidateToken() {
        accessToken = null;
        tokenExpiry = null;
    }

    /**
     * Execute GET request and return the raw response body as bytes.
     * Used for downloading iFlow ZIP bundles via the $value endpoint.
     *
     * @param urlOrPath Either a relative API path or a full URL
     * @return Response body as byte array
     */
    // @author Vikas Singh | Created: 2026-02-22
    public byte[] getBytes(String urlOrPath) throws IOException {
        String fullUrl;
        if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            fullUrl = urlOrPath;
        } else {
            fullUrl = config.getBaseUrl() + urlOrPath;
        }
        log.debug("GET (bytes) {}", fullUrl);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpGet request = new HttpGet(fullUrl);
                request.setHeader(HttpHeaders.ACCEPT, "application/zip, application/octet-stream, */*");
                request.setHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    int statusCode = response.getStatusLine().getStatusCode();

                    if (statusCode == 200) {
                        return EntityUtils.toByteArray(response.getEntity());
                    } else if (statusCode == 401 && attempt < maxRetries) {
                        log.warn("Got 401, refreshing token and retrying (attempt {}/{})", attempt, maxRetries);
                        invalidateToken();
                        continue;
                    } else if (statusCode == 429) {
                        log.warn("Rate limited (429), waiting before retry (attempt {}/{})", attempt, maxRetries);
                        Thread.sleep(retryDelayMs * attempt);
                        continue;
                    } else if (statusCode >= 500 && attempt < maxRetries) {
                        log.warn("Server error {}, retrying (attempt {}/{})", statusCode, attempt, maxRetries);
                        Thread.sleep(retryDelayMs);
                        continue;
                    } else {
                        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        throw new IOException(String.format(
                                "HTTP %d from %s: %s", statusCode, urlOrPath, body));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            }
        }
        throw new IOException("Max retries exceeded for: " + urlOrPath);
    }

    /**
     * Fetch CSRF token for write operations (if needed in future).
     */
    // @author Vikas Singh | Created: 2025-11-30
    public String fetchCsrfToken() throws IOException {
        HttpGet request = new HttpGet(config.getBaseUrl() + "/api/v1/");
        request.setHeader(HttpHeaders.AUTHORIZATION, getAuthHeader());
        request.setHeader("X-CSRF-Token", "Fetch");

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            return response.getFirstHeader("X-CSRF-Token") != null
                    ? response.getFirstHeader("X-CSRF-Token").getValue()
                    : null;
        }
    }

    // @author Vikas Singh | Created: 2025-11-30
    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
