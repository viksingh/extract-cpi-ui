# SAP CPI Artifact Extractor UI

A JavaFX desktop application for connecting to SAP Cloud Platform Integration (CPI) tenants, extracting artifact metadata via the OData v1 API, analyzing iFlow dependencies, and exporting results to Excel/CSV/JSON. Features deep extraction with BPMN bundle parsing, dependency graph analysis, unique interface tracing, credential inventory, and ECC endpoint classification.

## Features

### Connection & Authentication
- **OAuth2 & Basic Auth** — OAuth2 client credentials with token caching and auto-refresh, plus Basic auth for legacy Neo environments
- **Connection Profiles** — Save, load, and delete named connection profiles for quick switching between tenants
- **Load/Save Config** — Persist and restore connection settings to/from `.properties` files

### Extraction
- **Core Extraction** — Packages, Integration Flows, Runtime Status, Message Processing Logs (always extracted)
- **Deep Extraction** (enabled by default) — Downloads and parses iFlow BPMN bundles to extract adapters, mappings, scripts, configurations, and endpoints
- **Parallel Fetching** — Concurrent API calls (configurable threads, default 4) for packages, configurations, bundles, and MPL logs
- **Date Filter** — Filter artifacts by Modified Since, Created Since, or Deployed Since date
- **Package Filter** — Select/deselect individual packages with search, populated after Fetch Packages or extraction
- **Auto-save Snapshot** — Automatically saves a JSON snapshot after each extraction for offline use

### Analysis
- **Dependency Analysis** — Detects iFlow-to-iFlow linkages via ProcessDirect adapter address matching and JMS queue producer/consumer pairing
- **Unique Interface Tracing** — DFS-based end-to-end path tracing that treats PD/JMS-chained iFlows as a single logical interface, with cycle detection
- **Package Dependencies** — Aggregated package-level dependency view with cross-package indicators and link status
- **Flow Chains** — Direct producer-consumer links (ProcessDirect and JMS) with last used timestamps and runtime status
- **iFlow Usage Detection** — Identifies unused deployed iFlows (deployed but no MPL records in last 90 days)
- **Credential Inventory** — Scans adapter and process properties for 15+ security material types (OAuth2, SAML, PGP, certificates, etc.)
- **ECC Endpoint Classification** — Categorizes endpoints for ECC-to-S/4HANA migration planning (IDoc, RFC/BAPI, XI/SOAP, OData, REST, etc.)
- **Externalized Parameter Resolution** — Resolves `{{paramName}}` placeholders from flow Configuration objects in addresses and chains

### Results View (14 Tabs)

| Tab | Content |
|---|---|
| **Summary** | Text report with counts, status breakdown, and dependency summary |
| **Packages** | All Integration Packages with metadata (version, vendor, mode, dates) |
| **Flows** | All Integration Flows with runtime status, deployment info, sender/receiver |
| **Value Maps** | Value Mapping artifacts with version and runtime status |
| **Configs** | Flattened view of all externalized configuration parameters |
| **Runtime** | Runtime deployment status with error information |
| **iFlow Adapters** | Adapter configurations parsed from BPMN bundles (type, direction, protocol, address) |
| **iFlow Usage** | Execution metrics per iFlow — total, completed, failed, retry, escalated, last execution, unused detection |
| **Credentials** | Security materials and credential references detected across all flows |
| **ECC Endpoints** | Endpoints classified by protocol category for migration planning |
| **Flow Chains** | ProcessDirect and JMS producer-consumer links with usage timestamps |
| **Unique Interfaces** | End-to-end interface paths — chained flows shown as single interfaces, plus standalone flows |
| **Package Dependencies** | Package-level dependency aggregation with link status and usage data |
| **API Calls** | HTTP request log with method, path, status code, and duration |

### Export
- **Excel (.xlsx)** — Multi-sheet workbook with 18+ sheets, formatted headers, freeze panes, and auto-sized columns
- **CSV** — One file per entity type
- **JSON** — Full structured snapshot (loadable for offline viewing)
- **Export from Snapshot** — Load a snapshot and re-export without connecting to a tenant
- **Auto filename prefix** — Automatically set from profile name (e.g., profile "PRD" → prefix "prd_")

### Resilience
- **HTTP Retry** — Automatic retry with exponential backoff for 401 (token refresh), 429 (rate limiting), and 5xx errors
- **OData Pagination** — Handles 6 SAP OData response format variations and follows `d.__next`, `__next`, and `@odata.nextLink` links
- **XXE Prevention** — Secure XML parsing with external entity processing disabled

## Prerequisites

- **Java 17** or later
- **Maven 3.8+**
- **SAP CPI tenant** with API access (OData v1)
- Service key or credentials with read access to:
  - `IntegrationPackages`
  - `IntegrationDesigntimeArtifacts`
  - `IntegrationRuntimeArtifacts`
  - `ValueMappingDesigntimeArtifacts`
  - `Configurations`
  - `MessageProcessingLogs`

## Build

```bash
cd extract-cpi-ui
mvn clean package
```

This produces a shaded (fat) JAR at `target/cpi-artifact-extractor-ui-1.0.0.jar`.

## Run

```bash
# Via shaded JAR
java -jar target/cpi-artifact-extractor-ui-1.0.0.jar

# Or via Maven
mvn javafx:run
```

## Configuration

### Using the UI

The application provides a **Connection Settings** panel where you enter:

| Field | Description |
|---|---|
| Tenant URL | CPI tenant base URL (e.g. `https://tenant.it-cpi018.cfapps.eu10.hana.ondemand.com`) |
| Auth Type | `OAuth2` (recommended) or `Basic` |
| Token URL | OAuth2 token endpoint (e.g. `https://tenant.authentication.eu10.hana.ondemand.com/oauth/token`) |
| Client ID | OAuth2 client ID from service key |
| Client Secret | OAuth2 client secret |
| Username | Basic auth username (Neo environments) |
| Password | Basic auth password |

### Properties file

Create a `config.properties` file:

```properties
# CPI Connection (required)
cpi.base.url=https://tenant.it-cpi018.cfapps.eu10.hana.ondemand.com
cpi.auth.type=oauth2

# OAuth2 credentials
cpi.oauth.token.url=https://tenant.authentication.eu10.hana.ondemand.com/oauth/token
cpi.oauth.client.id=sb-xxxx
cpi.oauth.client.secret=xxxx

# Basic auth (alternative)
# cpi.auth.type=basic
# cpi.basic.username=S00xxxxx
# cpi.basic.password=xxxx

# HTTP resilience (optional)
http.connect.timeout.ms=30000
http.read.timeout.ms=60000
http.max.retries=3
http.retry.delay.ms=2000

# Parallel threads for API calls (default: 4)
api.parallel.threads=4

# Export settings
export.format=xlsx
export.output.dir=C:\temp\CPI Extracts
export.filename.prefix=cpi_artifacts
```

### Environment variables

All settings can be overridden with environment variables (highest precedence):

| Variable | Maps to |
|---|---|
| `CPI_BASE_URL` | `cpi.base.url` |
| `CPI_AUTH_TYPE` | `cpi.auth.type` |
| `CPI_OAUTH_TOKEN_URL` | `cpi.oauth.token.url` |
| `CPI_OAUTH_CLIENT_ID` | `cpi.oauth.client.id` |
| `CPI_OAUTH_CLIENT_SECRET` | `cpi.oauth.client.secret` |
| `CPI_BASIC_USERNAME` | `cpi.basic.username` |
| `CPI_BASIC_PASSWORD` | `cpi.basic.password` |

### Configuration precedence

1. Environment variables (highest)
2. External config file (loaded via UI or constructor)
3. Classpath `application.properties`
4. Built-in defaults

## Usage

### 1. Connect & Extract

1. Launch the application
2. Select a saved profile or enter connection details manually
3. Optionally click **Fetch Packages** to pre-populate the package filter
4. Select/deselect packages, configure date filter if needed
5. Choose export format and output directory
6. Click **Extract & Export**
7. Monitor progress via the progress bar and log panel

### 2. Review Results

Results appear across 14 tabs. Key analysis tabs:

- **iFlow Usage** — Find unused deployed iFlows (deployed but never executed)
- **Flow Chains** — See ProcessDirect and JMS linkages between iFlows
- **Unique Interfaces** — View end-to-end logical interfaces (chained iFlows count as one)
- **Package Dependencies** — Understand cross-package dependencies
- **Credentials** — Audit security materials across all flows
- **ECC Endpoints** — Identify ECC-specific protocols for S/4HANA migration planning

### 3. Load Snapshot

Click **Load Snapshot** to load a previously exported JSON snapshot for offline review without connecting to a CPI tenant. All tabs are populated including dependency analysis.

### 4. Export

Click **Export** to re-export loaded data. Exported files are saved with timestamps:

| Format | File Pattern |
|---|---|
| **Excel** | `prefix_YYYYMMDD_HHmmss.xlsx` |
| **CSV** | `prefix_YYYYMMDD_HHmmss_*.csv` |
| **JSON** | `prefix_YYYYMMDD_HHmmss.json` |
| **Snapshot** | `prefix_snapshot_YYYYMMDD_HHmmss.json` (auto-saved) |

### Excel Sheet Reference

The Excel export contains up to 18 sheets:

| Sheet | Description |
|---|---|
| Summary | Extraction metadata, artifact counts, status breakdown |
| Packages | All integration packages with full metadata |
| Integration Flows | All flows with deployment and runtime info |
| Value Mappings | All value mapping artifacts |
| Configurations | Externalized configuration parameters per flow |
| Runtime Status | Deployed artifact status with error details |
| iFlow Adapters | Adapter configurations from parsed BPMN bundles |
| iFlow Mappings | Message mappings and mapping files |
| iFlow Scripts | Script files with language and content snippets |
| iFlow Usage | Execution metrics with unused detection |
| ECC Endpoints | Protocol-classified endpoints for migration |
| Flow Chains | ProcessDirect/JMS links with usage timestamps |
| Unique Interfaces | End-to-end paths with chain details and last used |
| Package Dependencies | Package-level dependency aggregation |
| Circular Dependencies | Detected circular dependency cycles |
| Credentials | Security material inventory |
| Message Processing Logs | Raw MPL records |
| API Calls | HTTP request log |

## How It Works

### Phase 1: UI Initialization

```
Launcher.main() → CpiExtractorFxApp.start()
  → Load main.fxml layout (1100 x 800 window)
  → Apply styles.css stylesheet
  → MainController.initialize() — wire up combo boxes, tables, log appender
```

### Phase 2: Extraction (background thread, parallel)

```
onExtract() → JavaFX Task (daemon thread)
  → Build Properties from form fields
  → CpiConfiguration loads & validates
  → CpiHttpClient authenticates (OAuth2 token or Basic auth)
  → CpiApiService.extractAll()
      → Fetch packages (sequential)
      → Fetch flows per package (4 parallel threads)
      → Fetch configurations per flow (4 parallel threads)
      → Fetch runtime status (sequential)
      → Download & parse iFlow bundles (4 parallel threads)
      → Fetch MPL logs per flow (4 parallel threads)
      → OData pagination follows __next / @odata.nextLink
  → Export to selected format(s)
  → Auto-save snapshot (if enabled)
  → Return ExtractionResult to UI thread
```

### Phase 3: Results & Analysis (JavaFX Application Thread)

```
onSucceeded callback
  → populateResults(ExtractionResult)
      → Summary, Packages, Flows, Value Maps, Configs, Runtime tables
      → iFlow Adapters, Usage, Credentials, ECC Endpoints tables
      → API Calls table
  → populateDependencyTabs(result)
      → DependencyAnalysisService.analyze() — ProcessDirect + JMS detection
      → Package Dependencies table (aggregated with usage/status)
      → traceUniqueInterfaces() — DFS chain tracing
      → Unique Interfaces table (chains + standalone flows)
  → Switch to Summary tab
```

## Project Structure

```
extract-cpi-ui/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/sakiv/cpi/extractor/
    │   ├── Launcher.java                    # Shaded JAR entry point
    │   ├── CpiExtractorFxApp.java           # JavaFX Application (stage setup)
    │   ├── config/
    │   │   └── CpiConfiguration.java        # Hierarchical config loading & validation
    │   ├── model/
    │   │   ├── IntegrationPackage.java      # CPI package metadata
    │   │   ├── IntegrationFlow.java         # iFlow artifact with bundle content
    │   │   ├── ValueMapping.java            # Value mapping artifact
    │   │   ├── Configuration.java           # Externalized configuration parameter
    │   │   ├── RuntimeArtifact.java         # Runtime deployment status
    │   │   ├── MessageProcessingLog.java    # MPL record
    │   │   ├── ExtractionResult.java        # Container for all extracted data
    │   │   ├── ConnectionProfile.java       # Saved connection profile
    │   │   ├── IFlowContent.java            # Parsed BPMN bundle content
    │   │   ├── IFlowAdapter.java            # Adapter metadata
    │   │   ├── IFlowEndpoint.java           # Participant endpoint
    │   │   ├── IFlowMapping.java            # Message mapping
    │   │   ├── IFlowRoute.java              # Process route/activity
    │   │   ├── ScriptInfo.java              # Script file info
    │   │   ├── Dependency.java              # Flow-to-flow dependency edge
    │   │   ├── DependencyGraph.java         # Dependency graph with traversal
    │   │   ├── DependencyType.java          # Enum: PROCESS_DIRECT, JMS_QUEUE
    │   │   └── PackageDependency.java       # Package-level dependency aggregation
    │   ├── parser/
    │   │   └── IFlowXmlParser.java          # BPMN XML parser (adapters, mappings, scripts)
    │   ├── service/
    │   │   ├── CpiApiService.java           # OData API orchestration (parallel, pagination)
    │   │   ├── CpiHttpClient.java           # HTTP client (OAuth2/Basic, retry, logging)
    │   │   ├── DependencyAnalysisService.java # ProcessDirect/JMS analysis + interface tracing
    │   │   ├── SnapshotLoader.java          # JSON snapshot deserializer
    │   │   ├── ProfileManager.java          # Connection profile persistence
    │   │   └── ExtractionProgressCallback.java # Progress callback interface
    │   ├── export/
    │   │   ├── ExcelExporter.java           # Multi-sheet Excel export (18 sheets)
    │   │   ├── CsvExporter.java             # CSV export (one file per entity)
    │   │   └── JsonExporter.java            # Structured JSON snapshot export
    │   ├── util/
    │   │   └── DateFilterUtil.java          # Date parsing and formatting utilities
    │   └── ui/
    │       ├── MainController.java          # JavaFX FXML controller (14 tabs, all actions)
    │       └── TextAreaLogAppender.java     # Logback → TextArea real-time log appender
    └── resources/
        ├── fxml/main.fxml                   # UI layout (connection, options, 14 result tabs)
        ├── css/styles.css                   # Stylesheet
        ├── application.properties           # Default configuration
        └── logback.xml                      # Logging configuration
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| JavaFX (controls, FXML) | 21.0.2 | Desktop UI framework |
| Apache HttpClient | 4.5.14 | HTTP communication with CPI OData API |
| Jackson Databind + JSR310 | 2.17.0 | JSON parsing and serialization |
| Apache POI | 5.2.5 | Excel (.xlsx) export |
| Commons CSV | 1.10.0 | CSV export |
| SLF4J + Logback | 2.0.12 / 1.5.3 | Logging framework |
| Commons Lang3 | 3.14.0 | String and object utilities |
| JUnit 5 | 5.10.2 | Testing (test scope) |

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        JavaFX UI Layer                           │
│  MainController.java ← main.fxml + styles.css                   │
│  ┌──────────┬──────────┬──────────┬──────────┬───────────────┐  │
│  │ Summary  │ Packages │  Flows   │  Value   │ Configurations│  │
│  │          │          │          │  Maps    │               │  │
│  ├──────────┼──────────┼──────────┼──────────┼───────────────┤  │
│  │ Runtime  │ Adapters │  Usage   │ Creds    │ ECC Endpoints │  │
│  ├──────────┼──────────┼──────────┼──────────┼───────────────┤  │
│  │  Flow    │ Unique   │ Package  │ API      │               │  │
│  │  Chains  │ Intfcs   │ Deps     │ Calls    │               │  │
│  └──────────┴──────────┴──────────┴──────────┴───────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ Connection Profiles │ Package Filter │ Date Filter       │    │
│  │ Export Settings      │ Progress Bar   │ Log Panel         │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                  Configuration Layer                              │
│  CpiConfiguration + ProfileManager                               │
│  Env vars > Config file > application.properties > Defaults      │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                     Service Layer                                 │
│  CpiApiService — parallel extraction, OData pagination           │
│  CpiHttpClient — OAuth2/Basic auth, retry with backoff           │
│  DependencyAnalysisService — PD/JMS analysis, interface tracing  │
│  IFlowXmlParser — BPMN bundle parsing (adapters, scripts, maps)  │
│  SnapshotLoader — JSON snapshot → ExtractionResult               │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                  SAP CPI OData v1 API                             │
│  /api/v1/IntegrationPackages                                     │
│  /api/v1/IntegrationDesigntimeArtifacts                          │
│  /api/v1/IntegrationDesigntimeArtifacts('id')/$value  (bundles)  │
│  /api/v1/ValueMappingDesigntimeArtifacts                         │
│  /api/v1/IntegrationRuntimeArtifacts                             │
│  /api/v1/.../Configurations                                      │
│  /api/v1/MessageProcessingLogs                                   │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                     Export Layer                                   │
│  ExcelExporter → .xlsx (18-sheet workbook)                       │
│  CsvExporter   → .csv  (one file per entity)                    │
│  JsonExporter  → .json (full structured snapshot)                │
└──────────────────────────────────────────────────────────────────┘
```

## License

Internal project — SAP BTP Integration Suite management toolkit.
