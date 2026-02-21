# SAP CPI Artifact Extractor UI

A JavaFX desktop application that provides a graphical interface for connecting to an SAP Cloud Platform Integration (CPI) tenant, extracting all artifact metadata via the OData v1 API, and exporting the results to Excel, CSV, or JSON. This is the GUI counterpart to the CLI extractor, with tabbed result views, real-time logging, and snapshot loading.

## Features

- **Connection Settings Panel** — Configure Tenant URL, Auth Type (OAuth2/Basic), and all credential fields directly in the UI with dynamic field visibility
- **Selective Extraction** — Checkboxes to individually enable/disable extraction of Packages, Flows, Value Mappings, Configurations, and Runtime Status
- **Export Format Selection** — Choose between Excel (.xlsx), CSV, JSON, or All Formats via a dropdown
- **Tabbed Results View** — Six tabs displaying extraction results:
  - **Summary** — Text report with package, flow, value mapping, and runtime counts
  - **Packages** — Table with ID, name, description, version, vendor, mode, created/modified metadata
  - **Flows** — Table with ID, name, package, version, sender, receiver, runtime status, deployed version
  - **Value Maps** — Table with ID, name, description, package, version, runtime status
  - **Configurations** — Flattened table of all externalized parameters across all flows (artifact ID, key, value, data type)
  - **Runtime** — Table with artifact ID, name, type, version, status, deployed-by, deployed-on, error info
- **Progress Bar & Real-time Logging** — Live progress indicator and scrolling log panel showing extraction activity
- **Load/Save Config** — Persist and restore connection settings to/from `.properties` files via file chooser dialogs
- **Load Snapshot** — Load a previously exported JSON snapshot file for offline viewing without connecting to a CPI tenant
- **OAuth2 & Basic Auth** — Supports OAuth2 client credentials with token caching and auto-refresh, plus Basic auth for legacy Neo environments
- **HTTP Resilience** — Automatic retry with exponential backoff for 401 (token refresh), 429 (rate limiting), and 5xx (server errors)
- **OData Pagination** — Handles 6 SAP OData response format variations and follows `d.__next`, `__next`, and `@odata.nextLink` pagination links

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

Additional UI controls:

| Control | Description |
|---|---|
| Extract Packages | Checkbox to include Integration Packages |
| Extract Flows | Checkbox to include Integration Flows |
| Extract Value Mappings | Checkbox to include Value Mapping artifacts |
| Extract Configurations | Checkbox to include externalized configuration parameters |
| Extract Runtime Status | Checkbox to include runtime deployment status |
| Export Format | Dropdown: Excel (.xlsx), CSV, JSON, or All Formats |
| Output Directory | Directory path for exported files (with Browse button) |
| Filename Prefix | Prefix for exported filenames (default: `cpi_artifacts`) |

Use **Load Config** / **Save Config** to persist settings to a `.properties` file.

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

# Extraction flags (all default to true)
extract.packages=true
extract.flows=true
extract.valuemappings=true
extract.configurations=true
extract.runtime.status=true

# Export settings
export.format=xlsx
export.output.dir=./output
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
2. Enter connection details or click **Load Config** to load a `.properties` file
3. Select which artifact types to extract using the checkboxes
4. Choose the export format and output directory
5. Click **Extract**
6. Monitor progress in the log panel at the bottom

### 2. Review Results

Once extraction completes, results appear across six tabs:

| Tab | Content |
|---|---|
| **Summary** | Text report with total packages, flows, value mappings, runtime artifact counts, and status breakdown |
| **Packages** | Sortable table of all Integration Packages with full metadata |
| **Flows** | Sortable table of all Integration Flows with package association, sender/receiver, and runtime status |
| **Value Maps** | Sortable table of all Value Mapping artifacts |
| **Configurations** | Flattened table showing every externalized configuration parameter across all flows |
| **Runtime** | Sortable table of runtime deployment artifacts with status, version, and error information |

### 3. Load Snapshot

Click **Load Snapshot** to load a previously exported JSON snapshot file. This allows offline review of extraction results without connecting to a CPI tenant — useful for sharing results across teams or reviewing historical snapshots.

### 4. Export

Exported files are saved to the configured output directory with a timestamp in the filename:

| Format | File Pattern |
|---|---|
| **Excel** | `cpi_artifacts_YYYYMMDD_HHmmss.xlsx` |
| **CSV** | `cpi_artifacts_YYYYMMDD_HHmmss_*.csv` |
| **JSON** | `cpi_artifacts_YYYYMMDD_HHmmss.json` |

## How It Works

### Phase 1: UI Initialization

```
Launcher.main() → CpiExtractorFxApp.start()
  → Load main.fxml layout (1100 x 800 window)
  → Apply styles.css stylesheet
  → MainController.initialize() — wire up combo boxes, tables, log appender
```

### Phase 2: Extraction (background thread)

```
onExtract() → JavaFX Task (daemon thread)
  → Build Properties from form fields
  → Write temp config file → CpiConfiguration loads & validates
  → CpiHttpClient authenticates (OAuth2 token or Basic auth)
  → CpiApiService.extractAll()
      → Fetch packages → flows → value mappings → configurations → runtime
      → OData pagination follows __next / @odata.nextLink
  → Export to selected format(s)
  → Return ExtractionResult to UI thread
```

### Phase 3: Results Display

```
onSucceeded callback (JavaFX Application Thread)
  → populateResults(ExtractionResult)
      → Summary text area
      → Packages table (PropertyValueFactory bindings)
      → Flows table
      → Value Maps table
      → Configurations table (flattened ConfigRow records)
      → Runtime table
  → Switch to Summary tab
```

## Project Structure

```
extract-cpi-ui/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/sap/cpi/extractor/
    │   ├── Launcher.java                    # Shaded JAR entry point (delegates to FxApp)
    │   ├── CpiExtractorFxApp.java           # JavaFX Application (stage setup, FXML loading)
    │   ├── config/
    │   │   └── CpiConfiguration.java        # Hierarchical config loading & validation
    │   ├── model/
    │   │   ├── IntegrationPackage.java      # CPI package metadata
    │   │   ├── IntegrationFlow.java         # CPI iFlow design-time artifact
    │   │   ├── ValueMapping.java            # Value mapping artifact
    │   │   ├── Configuration.java           # Externalized configuration parameter
    │   │   ├── RuntimeArtifact.java         # Runtime deployment status
    │   │   └── ExtractionResult.java        # Container for all extracted data + summary
    │   ├── service/
    │   │   ├── CpiHttpClient.java           # HTTP client with OAuth2/Basic auth & retry
    │   │   ├── CpiApiService.java           # OData API abstraction (pagination, format handling)
    │   │   └── SnapshotLoader.java          # Jackson-based JSON snapshot deserializer
    │   ├── export/
    │   │   ├── ExcelExporter.java           # Multi-sheet Excel (.xlsx) export
    │   │   ├── CsvExporter.java             # CSV export (one file per entity)
    │   │   └── JsonExporter.java            # Structured JSON export
    │   └── ui/
    │       ├── MainController.java          # JavaFX FXML controller (form, tables, actions)
    │       └── TextAreaLogAppender.java     # Logback → TextArea appender for real-time logs
    └── resources/
        ├── fxml/main.fxml                   # UI layout definition
        ├── css/styles.css                   # Stylesheet
        ├── application.properties           # Default configuration
        └── logback.xml                      # Logging configuration
```

### Key classes

| Class | Purpose |
|---|---|
| `MainController` | JavaFX FXML controller — manages form fields, extraction checkboxes, export settings, six result tabs, progress bar, and log panel |
| `CpiExtractorFxApp` | JavaFX Application — loads FXML, applies stylesheet, configures the primary stage (1100x800, min 1000x750) |
| `Launcher` | Shaded JAR entry point that delegates to `CpiExtractorFxApp.main()` to avoid JavaFX module issues |
| `SnapshotLoader` | Loads a previously exported JSON snapshot into an `ExtractionResult` for offline viewing |
| `CpiApiService` | OData v1 abstraction handling 6 SAP response format variations, pagination (`__next`, `@odata.nextLink`), and metadata stripping |
| `CpiHttpClient` | HTTP client with OAuth2 token caching/refresh, Basic auth, and exponential backoff retry for 401/429/5xx |
| `TextAreaLogAppender` | Custom Logback appender that routes log messages to the JavaFX TextArea on the Application Thread |

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
│  │ Tab      │ Tab      │  Tab     │  Maps    │ Tab           │  │
│  └──────────┴──────────┴──────────┴──────────┴───────────────┘  │
│  ┌──────────┐  ┌───────────────┐  ┌──────────────────────────┐  │
│  │ Runtime  │  │ Progress Bar  │  │ Log Panel (TextArea)     │  │
│  │ Tab      │  │ + Label       │  │ via TextAreaLogAppender  │  │
│  └──────────┘  └───────────────┘  └──────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ Connection Settings  │ Extraction Checkboxes │ Export    │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                  Configuration Layer                              │
│  CpiConfiguration                                                │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────────┐ │
│  │  Built-in   │ │ Classpath  │ │  External  │ │ Environment  │ │
│  │  Defaults   │ │ Properties │ │  Config    │ │  Variables   │ │
│  └────────────┘ └────────────┘ └────────────┘ └──────────────┘ │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                     Service Layer                                 │
│  CpiApiService (OData pagination, format handling)               │
│  CpiHttpClient (OAuth2/Basic auth, retry with backoff)           │
│  SnapshotLoader (JSON snapshot → ExtractionResult)               │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                  SAP CPI OData v1 API                             │
│  /api/v1/IntegrationPackages                                     │
│  /api/v1/IntegrationDesigntimeArtifacts                          │
│  /api/v1/ValueMappingDesigntimeArtifacts                         │
│  /api/v1/IntegrationRuntimeArtifacts                             │
│  /api/v1/.../Configurations                                      │
└──────────────────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│                     Export Layer                                   │
│  ExcelExporter → .xlsx (multi-sheet workbook)                    │
│  CsvExporter   → .csv  (one file per entity)                    │
│  JsonExporter  → .json (full structured snapshot)                │
└──────────────────────────────────────────────────────────────────┘
```

## License

Internal project — SAP BTP Integration Suite management toolkit.
