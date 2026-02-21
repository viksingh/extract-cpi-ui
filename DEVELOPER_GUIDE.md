# SAP CPI Artifact Extractor — Developer Guide

> **Project:** `extract-cpi-ui`
> **Package:** `com.sap.cpi.extractor`
> **Java:** 17 | **JavaFX:** 21.0.2 | **Build:** Maven

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Project Structure](#project-structure)
3. [Architecture Diagram](#architecture-diagram)
4. [Dependency Summary](#dependency-summary)
5. [Package: `config`](#package-config)
6. [Package: `model`](#package-model)
7. [Package: `service`](#package-service)
8. [Package: `export`](#package-export)
9. [Package: `ui`](#package-ui)
10. [Entry Points](#entry-points)
11. [Resources](#resources)
12. [Build & Run](#build--run)
13. [Configuration Reference](#configuration-reference)

---

## Project Overview

The SAP CPI Artifact Extractor is a JavaFX desktop application that connects to an SAP Integration Suite (Cloud Platform Integration) tenant via its OData API, extracts metadata about all integration artifacts, and exports the data into Excel, CSV, and/or JSON formats.

It supports:
- OAuth2 Client Credentials and Basic Authentication
- Extraction of packages, iFlows, value mappings, configurations, and runtime status
- Multi-format export (XLSX, CSV, JSON, or all three)
- Real-time logging via a custom Logback appender piped to the UI
- Retry logic with exponential backoff for transient API errors
- Pagination handling across six different OData response formats

---

## Project Structure

```
extract-cpi-ui/
├── pom.xml                                  # Maven build config
├── config.properties.template               # Template for external credentials
├── .gitignore
│
├── src/main/java/com/sap/cpi/extractor/
│   ├── Launcher.java                        # Fat-JAR entry point
│   ├── CpiExtractorFxApp.java              # JavaFX Application class
│   │
│   ├── config/
│   │   └── CpiConfiguration.java           # Multi-source config loader
│   │
│   ├── model/
│   │   ├── ExtractionResult.java           # Top-level result container
│   │   ├── IntegrationPackage.java         # CPI package entity
│   │   ├── IntegrationFlow.java            # iFlow design-time entity
│   │   ├── ValueMapping.java               # Value mapping entity
│   │   ├── Configuration.java              # Externalized parameter
│   │   └── RuntimeArtifact.java            # Deployed artifact status
│   │
│   ├── service/
│   │   ├── CpiHttpClient.java             # HTTP client with OAuth2/Basic auth
│   │   └── CpiApiService.java             # OData API service layer
│   │
│   ├── export/
│   │   ├── ExcelExporter.java             # Multi-sheet XLSX export
│   │   ├── CsvExporter.java               # Multi-file CSV export
│   │   └── JsonExporter.java              # Full JSON export
│   │
│   └── ui/
│       ├── MainController.java            # JavaFX controller (516 lines)
│       └── TextAreaLogAppender.java       # Logback → TextArea appender
│
└── src/main/resources/
    ├── application.properties              # Default config (API paths, settings)
    ├── logback.xml                         # Logging configuration
    ├── fxml/
    │   └── main.fxml                       # UI layout (145 lines)
    └── css/
        └── styles.css                      # SAP-themed styling (217 lines)
```

---

## Architecture Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                        JavaFX UI Layer                         │
│  ┌──────────────────┐  ┌─────────────────────────────────────┐ │
│  │  main.fxml        │  │  MainController.java                │ │
│  │  (layout)         │──│  - Form bindings                    │ │
│  │                   │  │  - Event handlers                   │ │
│  │  styles.css       │  │  - Table population                 │ │
│  │  (theme)          │  │  - Background Task orchestration    │ │
│  └──────────────────┘  └──────────┬──────────────────────────┘ │
│                                   │                             │
│           TextAreaLogAppender ◄────┘  (real-time log display)   │
└───────────────────────────────────┬─────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
          ┌─────────────┐  ┌──────────────┐  ┌───────────┐
          │ CpiApiService│  │ ExcelExporter│  │CsvExporter│
          │              │  │              │  │           │
          │ - extractAll │  │ - 6 sheets   │  │ - 5 files │
          │ - pagination │  │ - color-code │  │           │
          │ - OData parse│  └──────────────┘  └───────────┘
          └──────┬───────┘                    ┌────────────┐
                 │                            │JsonExporter│
                 ▼                            │            │
          ┌──────────────┐                    │ - Jackson   │
          │CpiHttpClient │                    └────────────┘
          │              │
          │ - OAuth2     │
          │ - Basic Auth │
          │ - Retry logic│
          └──────┬───────┘
                 │
                 ▼
          ┌──────────────┐
          │CpiConfigura- │
          │tion          │
          │              │
          │ - Properties │
          │ - Env vars   │
          │ - Validation │
          └──────────────┘
```

---

## Dependency Summary

| Dependency | Version | Purpose |
|---|---|---|
| `javafx-controls` | 21.0.2 | JavaFX UI controls (TableView, TabPane, etc.) |
| `javafx-fxml` | 21.0.2 | FXML loader for declarative UI layout |
| `httpclient` (Apache) | 4.5.14 | HTTP calls to CPI OData API |
| `jackson-databind` | 2.17.0 | JSON parsing and serialization |
| `jackson-datatype-jsr310` | 2.17.0 | Java 8+ date/time support for Jackson |
| `poi-ooxml` (Apache POI) | 5.2.5 | Excel (.xlsx) file generation |
| `commons-csv` | 1.10.0 | CSV file generation |
| `slf4j-api` | 2.0.12 | Logging facade |
| `logback-classic` | 1.5.3 | Logging implementation |
| `commons-lang3` | 3.14.0 | String utilities |
| `junit-jupiter` | 5.10.2 | Unit testing (test scope) |

---

## Package: `config`

### `CpiConfiguration.java` (202 lines)

Manages all application configuration with a layered loading hierarchy.

**Loading Order (each layer overrides the previous):**
1. **Hard-coded defaults** — timeout values, export settings, extraction toggles
2. **Classpath** — `application.properties` bundled in the JAR
3. **External file** — user-provided `.properties` file with credentials
4. **Environment variables** — `CPI_BASE_URL` → `cpi.base.url`, etc.

**Constructors:**

| Constructor | Description |
|---|---|
| `CpiConfiguration()` | Loads defaults + classpath + environment only |
| `CpiConfiguration(String configFilePath)` | Loads defaults + classpath + external file + environment |

**Key Methods:**

| Method | Signature | Description |
|---|---|---|
| `get` | `String get(String key)` | Get a property value by key |
| `get` | `String get(String key, String defaultValue)` | Get with fallback default |
| `getInt` | `int getInt(String key, int defaultValue)` | Parse integer property |
| `getBoolean` | `boolean getBoolean(String key, boolean defaultValue)` | Parse boolean property |
| `validate` | `void validate()` | Validates required fields based on auth type; throws `IllegalStateException` if missing |

**Convenience Accessors:**

| Method | Property Key | Default |
|---|---|---|
| `getBaseUrl()` | `cpi.base.url` | *(required)* |
| `getAuthType()` | `cpi.auth.type` | `"oauth2"` |
| `getOAuthTokenUrl()` | `cpi.oauth.token.url` | *(required for OAuth2)* |
| `getOAuthClientId()` | `cpi.oauth.client.id` | *(required for OAuth2)* |
| `getOAuthClientSecret()` | `cpi.oauth.client.secret` | *(required for OAuth2)* |
| `getBasicUsername()` | `cpi.basic.username` | *(required for Basic)* |
| `getBasicPassword()` | `cpi.basic.password` | *(required for Basic)* |
| `getExportFormat()` | `export.format` | `"xlsx"` |
| `getOutputDir()` | `export.output.dir` | `"./output"` |
| `getFilenamePrefix()` | `export.filename.prefix` | `"cpi_artifacts"` |

**Environment Variable Mapping:**

| Environment Variable | Maps To |
|---|---|
| `CPI_BASE_URL` | `cpi.base.url` |
| `CPI_AUTH_TYPE` | `cpi.auth.type` |
| `CPI_OAUTH_TOKEN_URL` | `cpi.oauth.token.url` |
| `CPI_OAUTH_CLIENT_ID` | `cpi.oauth.client.id` |
| `CPI_OAUTH_CLIENT_SECRET` | `cpi.oauth.client.secret` |
| `CPI_BASIC_USERNAME` | `cpi.basic.username` |
| `CPI_BASIC_PASSWORD` | `cpi.basic.password` |

---

## Package: `model`

All model classes use `@JsonIgnoreProperties(ignoreUnknown = true)` and `@JsonProperty("FieldName")` annotations for Jackson deserialization from OData JSON responses.

### `ExtractionResult.java` (82 lines)

Top-level container holding all extracted data.

| Field | Type | Description |
|---|---|---|
| `extractedAt` | `LocalDateTime` | Timestamp of extraction (set in constructor) |
| `tenantUrl` | `String` | CPI tenant base URL |
| `packages` | `List<IntegrationPackage>` | All extracted packages |
| `allFlows` | `List<IntegrationFlow>` | All iFlows across all packages |
| `allValueMappings` | `List<ValueMapping>` | All value mappings |
| `runtimeArtifacts` | `List<RuntimeArtifact>` | All deployed runtime artifacts |

**Methods:**

| Method | Description |
|---|---|
| `computeSummary()` | Counts totals, STARTED artifacts, and ERROR artifacts |
| `getSummary()` | Returns a formatted multi-line summary string |

### `IntegrationPackage.java` (138 lines)

Represents a CPI integration package.

| Field | JSON Property | Type |
|---|---|---|
| `id` | `Id` | `String` |
| `name` | `Name` | `String` |
| `description` | `Description` | `String` |
| `shortText` | `ShortText` | `String` |
| `version` | `Version` | `String` |
| `vendor` | `Vendor` | `String` |
| `mode` | `Mode` | `String` |
| `supportedPlatform` | `SupportedPlatform` | `String` |
| `modifiedBy` | `ModifiedBy` | `String` |
| `creationDate` | `CreationDate` | `String` |
| `modifiedDate` | `ModifiedDate` | `String` |
| `createdBy` | `CreatedBy` | `String` |
| `products` | `Products` | `String` |
| `keywords` | `Keywords` | `String` |
| `countries` | `Countries` | `String` |
| `industries` | `Industries` | `String` |
| `lineOfBusiness` | `LineOfBusiness` | `String` |
| `resourceId` | `ResourceId` | `String` |
| `integrationFlows` | *(runtime)* | `List<IntegrationFlow>` |
| `valueMappings` | *(runtime)* | `List<ValueMapping>` |

### `IntegrationFlow.java` (122 lines)

Represents a CPI iFlow design-time artifact.

| Field | JSON Property | Type | Notes |
|---|---|---|---|
| `id` | `Id` | `String` | Primary key |
| `version` | `Version` | `String` | Design-time version |
| `packageId` | `PackageId` | `String` | Parent package |
| `name` | `Name` | `String` | |
| `description` | `Description` | `String` | |
| `sender` | `Sender` | `String` | |
| `receiver` | `Receiver` | `String` | |
| `createdBy` | `CreatedBy` | `String` | |
| `createdAt` | `CreatedAt` | `String` | OData date format |
| `modifiedBy` | `ModifiedBy` | `String` | |
| `modifiedAt` | `ModifiedAt` | `String` | OData date format |
| `artifactContent` | `ArtifactContent` | `String` | Base64 content |
| `runtimeStatus` | *(enriched)* | `String` | Populated from RuntimeArtifact |
| `deployedVersion` | *(enriched)* | `String` | Populated from RuntimeArtifact |
| `deployedBy` | *(enriched)* | `String` | Populated from RuntimeArtifact |
| `deployedAt` | *(enriched)* | `String` | Populated from RuntimeArtifact |
| `runtimeError` | *(enriched)* | `String` | Populated from RuntimeArtifact |
| `configurations` | *(enriched)* | `List<Configuration>` | Populated by separate API call |

### `ValueMapping.java` (76 lines)

| Field | JSON Property | Type |
|---|---|---|
| `id` | `Id` | `String` |
| `version` | `Version` | `String` |
| `packageId` | `PackageId` | `String` |
| `name` | `Name` | `String` |
| `description` | `Description` | `String` |
| `createdBy` | `CreatedBy` | `String` |
| `createdAt` | `CreatedAt` | `String` |
| `modifiedBy` | `ModifiedBy` | `String` |
| `modifiedAt` | `ModifiedAt` | `String` |
| `runtimeStatus` | *(enriched)* | `String` |

### `Configuration.java` (40 lines)

Represents an externalized iFlow parameter.

| Field | JSON Property | Type |
|---|---|---|
| `parameterKey` | `ParameterKey` | `String` |
| `parameterValue` | `ParameterValue` | `String` |
| `dataType` | `DataType` | `String` |
| `artifactId` | *(set at runtime)* | `String` |

### `RuntimeArtifact.java` (64 lines)

Represents a deployed artifact with runtime status.

| Field | JSON Property | Type |
|---|---|---|
| `id` | `Id` | `String` |
| `version` | `Version` | `String` |
| `name` | `Name` | `String` |
| `type` | `Type` | `String` |
| `deployedBy` | `DeployedBy` | `String` |
| `deployedOn` | `DeployedOn` | `String` |
| `status` | `Status` | `String` |
| `errorInformation` | `ErrorInformation` | `String` |

---

## Package: `service`

### `CpiHttpClient.java` (186 lines)

Low-level HTTP client for CPI OData API calls. Handles authentication and retries.

**Constructor:** `CpiHttpClient(CpiConfiguration config)`

Reads timeout and retry settings from config, builds an Apache `CloseableHttpClient`.

**Methods:**

| Method | Signature | Description |
|---|---|---|
| `get` | `String get(String urlOrPath)` | Executes a GET request. Accepts relative paths (appended to base URL) or full URLs (for pagination). Returns the response body as a string. Retries on 401 (refreshes token), 429 (rate limit — waits), and 5xx (server error). |
| `getAuthHeader` | `String getAuthHeader()` | Returns `Bearer {token}` or `Basic {base64}` based on auth type. |
| `getOAuth2Token` | `synchronized String getOAuth2Token()` | Obtains an OAuth2 access token via Client Credentials grant. Caches the token and refreshes it 60 seconds before expiry. Thread-safe via `synchronized`. |
| `invalidateToken` | `void invalidateToken()` | Clears cached OAuth2 token (called on 401 before retry). |
| `fetchCsrfToken` | `String fetchCsrfToken()` | Fetches a CSRF token for future write operations. |
| `close` | `void close()` | Closes the underlying HTTP client. Implements `Closeable`. |

**Retry Logic:**
- **401 Unauthorized** — Invalidates cached token, retries (up to `maxRetries`)
- **429 Rate Limited** — Waits `retryDelayMs * attempt` (exponential backoff), retries
- **5xx Server Error** — Waits `retryDelayMs`, retries
- **Other errors** — Throws `IOException` immediately

### `CpiApiService.java` (463 lines)

High-level service layer that orchestrates all CPI OData API interactions.

**Constructor:** `CpiApiService(CpiConfiguration config, CpiHttpClient httpClient)`

**API Methods:**

| Method | Return Type | Description |
|---|---|---|
| `getIntegrationPackages()` | `List<IntegrationPackage>` | Fetches all packages from `/api/v1/IntegrationPackages` |
| `getPackageFlows(packageId)` | `List<IntegrationFlow>` | Fetches iFlows for a specific package |
| `getPackageValueMappings(packageId)` | `List<ValueMapping>` | Fetches value mappings for a specific package |
| `getAllIntegrationFlows()` | `List<IntegrationFlow>` | Fetches all iFlows from `/api/v1/IntegrationDesigntimeArtifacts` |
| `getAllValueMappings()` | `List<ValueMapping>` | Fetches all value mappings |
| `getConfigurations(artifactId, version)` | `List<Configuration>` | Fetches externalized parameters for a specific iFlow version |
| `getRuntimeArtifacts()` | `List<RuntimeArtifact>` | Fetches all deployed runtime artifacts |
| `getRuntimeStatusMap()` | `Map<String, RuntimeArtifact>` | Builds a lookup map (ID → RuntimeArtifact) |

**Orchestration Method:**

| Method | Description |
|---|---|
| `extractAll()` | Full extraction workflow: (1) fetches packages + per-package flows/value mappings, (2) fetches configurations for each flow, (3) fetches runtime status and enriches flows with deployment info. Returns a complete `ExtractionResult`. |

**Internal Methods (OData Handling):**

| Method | Description |
|---|---|
| `fetchAll(endpoint, entityClass)` | Generic paginated fetch. Adds `$format=json`, calls `httpClient.get()` in a loop following pagination links. Detects XML responses and throws. Delegates to `extractResultsArray()` and `stripODataMetadata()`. |
| `extractResultsArray(root, endpoint)` | Handles **6 different OData response formats**: (1) `{"d":{"results":[...]}}`, (2) `{"d":{"Id":"...",...}}`, (3) `{"results":[...]}`, (4) `[...]`, (5) `{"value":[...]}`, (6) `{"error":{...}}`. |
| `findNextPageUrl(root)` | Searches for pagination links in `d.__next`, `__next`, or `@odata.nextLink`. Converts absolute URLs to relative paths. |
| `stripODataMetadata(node)` | Removes OData V2 system fields (`__metadata`, `__deferred`, `__count`), flattens expanded navigation properties (`{"results":[...]}` → `[...]`), and recursively cleans nested entities. |

---

## Package: `export`

### `ExcelExporter.java` (360 lines)

Exports `ExtractionResult` to a multi-sheet `.xlsx` workbook.

**Method:** `String export(ExtractionResult result, String outputDir, String filenamePrefix)`

Returns the absolute path to the generated file.

**Sheets Created:**

| Sheet | Columns | Notes |
|---|---|---|
| **Summary** | Key-value pairs | Title, tenant URL, timestamp, counts |
| **Packages** | ID, Name, Description, Version, Vendor, Mode, Created/Modified By/Date | 10 columns |
| **Flows** | ID, Name, Description, Package, Version, Sender, Receiver, Runtime Status, Deployed Version, Created/Modified By | 10+ columns |
| **Value Mappings** | ID, Name, Description, Package, Version, Created/Modified By, Runtime Status | 9 columns |
| **Configurations** | Artifact ID, Artifact Name, Parameter Key, Parameter Value, Data Type | Flattened from nested flow configs |
| **Runtime** | Artifact ID, Name, Type, Version, Status, Deployed By, Deployed On, Error Info | Conditional formatting: RED for ERROR, GREEN for STARTED |

**Styling:**
- Header row: Dark blue background (`#354a5f`), white bold text, frozen first row
- Auto-filter on header rows
- Auto-sized columns (capped at 15000 width units)
- Error cells in red, success cells in green

**Helper Methods:**

| Method | Description |
|---|---|
| `createSummarySheet()` | Writes title and key statistics |
| `createPackagesSheet()` | Writes all package metadata |
| `createFlowsSheet()` | Writes flow metadata with runtime status |
| `createValueMappingsSheet()` | Writes value mapping metadata |
| `createConfigurationsSheet()` | Flattens configurations from all flows |
| `createRuntimeSheet()` | Writes runtime status with conditional colors |
| `formatCpiDate(String)` | Converts OData `/Date(epoch)/` format to human-readable |
| `nullSafe(String)` | Handles nulls and truncates to Excel's 32767-char limit |
| `autoSizeColumns(Sheet, int)` | Auto-fits column widths with max cap |

### `CsvExporter.java` (138 lines)

Exports to five separate CSV files using Apache Commons CSV.

**Method:** `String export(ExtractionResult result, String outputDir, String filenamePrefix)`

**Files Generated:**

| File | Contents | Columns |
|---|---|---|
| `{prefix}_{timestamp}_packages.csv` | All packages | 12 columns |
| `{prefix}_{timestamp}_flows.csv` | All iFlows | 15 columns (includes runtime) |
| `{prefix}_{timestamp}_valuemappings.csv` | All value mappings | 9 columns |
| `{prefix}_{timestamp}_configurations.csv` | All configs (flattened) | 5 columns |
| `{prefix}_{timestamp}_runtime.csv` | All runtime artifacts | 8 columns |

### `JsonExporter.java` (43 lines)

Exports the entire `ExtractionResult` as pretty-printed JSON.

**Method:** `String export(ExtractionResult result, String outputDir, String filenamePrefix)`

Uses Jackson `ObjectMapper` with `JavaTimeModule`, `INDENT_OUTPUT` enabled, and `WRITE_DATES_AS_TIMESTAMPS` disabled so `LocalDateTime` fields serialize as ISO-8601 strings.

---

## Package: `ui`

### `MainController.java` (516 lines)

The JavaFX controller managing the entire UI and extraction workflow.

**FXML Bindings (key fields):**

| Category | Fields |
|---|---|
| Connection | `tenantUrlField`, `authTypeCombo`, `oauthTokenUrlField`, `oauthClientIdField`, `oauthClientSecretField`, `basicUsernameField`, `basicPasswordField` |
| Options | `extractPackagesCb`, `extractFlowsCb`, `extractValueMappingsCb`, `extractConfigurationsCb`, `extractRuntimeCb` |
| Export | `exportFormatCombo`, `outputDirField`, `filenamePrefixField` |
| Results | `resultsTabPane`, `summaryTextArea`, `packagesTable`, `flowsTable`, `valueMapsTable`, `configsTable`, `runtimeTable` |
| Progress | `logTextArea`, `progressBar`, `progressLabel`, `extractButton` |

**Methods:**

| Method | Trigger | Description |
|---|---|---|
| `initialize()` | FXML load | Sets up `TextAreaLogAppender`, initializes `authTypeCombo` with OAuth2/Basic options, sets up all table columns, selects OAuth2 as default |
| `onAuthTypeChanged()` | Auth combo change | Toggles visibility of OAuth2 vs Basic auth fields |
| `onLoadConfig()` | Load Config button | Opens a `FileChooser` for `.properties` files, loads values into form fields |
| `onSaveConfig()` | Save Config button | Saves current form values to a `.properties` file |
| `onBrowseOutputDir()` | Browse button | Opens a `DirectoryChooser` for output directory |
| `onExtract()` | Extract button | **Main workflow**: validates fields → builds `Properties` from form → writes temp config file → creates `CpiConfiguration` → creates `CpiHttpClient` → calls `CpiApiService.extractAll()` → exports in selected format → populates UI tables. Runs on a background `javafx.concurrent.Task` thread. |
| `populateResults(result)` | After extraction | Fills all 6 tabs: summary text, packages table, flows table, value maps table, configs table (using `ConfigRow` records), runtime table |

**Table Initialization Methods:**

| Method | Columns |
|---|---|
| `initPackagesTable()` | Package ID, Name, Description, Version, Vendor, Mode, Created By, Creation Date, Modified By, Modified Date |
| `initFlowsTable()` | Flow ID, Name, Description, Package ID, Version, Sender, Receiver, Runtime Status, Deployed Version, Created/Modified By |
| `initValueMapsTable()` | ID, Name, Description, Package ID, Version, Created/Modified By, Runtime Status |
| `initConfigsTable()` | Artifact ID, Artifact Name, Parameter Key, Parameter Value, Data Type |
| `initRuntimeTable()` | Artifact ID, Name, Type, Version, Status, Deployed By, Deployed On, Error Info |

**Inner Record:**

```java
public record ConfigRow(String artifactId, String artifactName,
                        String parameterKey, String parameterValue, String dataType) {}
```

Used to flatten nested Configuration objects for the Configs tab table.

### `TextAreaLogAppender.java` (58 lines)

Custom Logback appender that pipes log messages to a JavaFX `TextArea`.

| Method | Description |
|---|---|
| `static setTextArea(TextArea ta)` | Sets the target TextArea (called once during `initialize()`) |
| `setEncoder(Encoder)` / `getEncoder()` | Configures the Logback encoder for message formatting |
| `start()` | Starts the encoder if set, then calls `super.start()` |
| `append(ILoggingEvent event)` | Formats the log event, posts `textArea.appendText()` via `Platform.runLater()`, auto-scrolls to bottom |

---

## Entry Points

### `Launcher.java` (7 lines)

```java
public class Launcher {
    public static void main(String[] args) {
        CpiExtractorFxApp.main(args);
    }
}
```

The fat-JAR manifest points to `Launcher` as the main class. This avoids the JavaFX module system issue where the main class extends `Application` directly.

### `CpiExtractorFxApp.java` (25 lines)

```java
public class CpiExtractorFxApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Loads /fxml/main.fxml, applies /css/styles.css
        // Window: 1100x800, min 1000x750
        // Title: "SAP CPI Artifact Extractor"
    }
}
```

---

## Resources

### `application.properties`

Default configuration bundled in the JAR. Contains:
- API endpoint paths (e.g., `/api/v1/IntegrationPackages`)
- Export settings (format, output dir, prefix)
- Extraction toggles (packages, flows, value mappings, configurations, runtime)
- HTTP settings (timeouts, retries)

### `logback.xml`

Three appenders:
- **CONSOLE** — `HH:mm:ss.SSS [level] logger - message`
- **FILE** — `logs/cpi-extractor.log` with full timestamps
- **TEXTAREA** — custom `TextAreaLogAppender` with short format

Logger levels: `com.sap.cpi.extractor` at DEBUG, `org.apache.http` at INFO.

### `main.fxml` (145 lines)

Declares the full UI layout: connection settings grid, extraction options checkboxes, export settings grid, action buttons bar, 6-tab results pane, and log section with progress bar.

### `styles.css` (217 lines)

SAP-inspired blue theme with styles for: root container, titled panes, text fields, combo boxes, checkboxes, action/primary/browse buttons, result tabs, data tables (dark headers, alternating rows), summary/log text areas (log area uses dark theme), and progress bar.

---

## Build & Run

```bash
# Build (produces a ~30MB shaded JAR)
mvn clean package

# Run via Maven
mvn javafx:run

# Run the JAR directly
java -jar target/cpi-artifact-extractor-ui-1.0.0.jar
```

---

## Configuration Reference

### Minimal External Config (`config.properties`)

```properties
# Required
cpi.base.url=https://tenant.it-cpiXXX.cfapps.region.hana.ondemand.com

# OAuth2
cpi.auth.type=oauth2
cpi.oauth.token.url=https://subdomain.authentication.region.hana.ondemand.com/oauth/token
cpi.oauth.client.id=your-client-id
cpi.oauth.client.secret=your-client-secret

# OR Basic
# cpi.auth.type=basic
# cpi.basic.username=your-username
# cpi.basic.password=your-password
```

### All Configurable Properties

| Property | Default | Description |
|---|---|---|
| `cpi.base.url` | *(required)* | CPI tenant URL |
| `cpi.auth.type` | `oauth2` | `oauth2` or `basic` |
| `cpi.oauth.token.url` | *(required for oauth2)* | OAuth2 token endpoint |
| `cpi.oauth.client.id` | *(required for oauth2)* | OAuth2 client ID |
| `cpi.oauth.client.secret` | *(required for oauth2)* | OAuth2 client secret |
| `cpi.basic.username` | *(required for basic)* | Basic auth username |
| `cpi.basic.password` | *(required for basic)* | Basic auth password |
| `export.format` | `xlsx` | `xlsx`, `csv`, `json`, or `all` |
| `export.output.dir` | `./output` | Output directory |
| `export.filename.prefix` | `cpi_artifacts` | Filename prefix |
| `extract.packages` | `true` | Extract packages |
| `extract.flows` | `true` | Extract iFlows |
| `extract.valuemappings` | `true` | Extract value mappings |
| `extract.configurations` | `true` | Extract configurations |
| `extract.runtime.status` | `true` | Extract runtime status |
| `http.connect.timeout.ms` | `30000` | Connection timeout |
| `http.read.timeout.ms` | `60000` | Read timeout |
| `http.max.retries` | `3` | Maximum retry attempts |
| `http.retry.delay.ms` | `2000` | Base delay between retries |
