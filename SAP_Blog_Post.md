# Extracting SAP CPI Artifact Metadata at Scale: A Must-Have Tool for ECC-to-S/4HANA Migrations

**Tags:** SAP BTP, SAP Integration Suite, Cloud Platform Integration, S/4HANA Migration, ECC Migration, Integration Assessment, OData API

---

## The Challenge: You're Migrating to S/4HANA — But What About Your Integrations?

If your organization is in the midst of — or planning — a migration from SAP ECC to SAP S/4HANA, you already know the enormity of the undertaking. Business processes are redesigned, custom code is adapted, and data is converted. But there is one area that is consistently underestimated and under-documented: **your integration landscape**.

Over the years, SAP Cloud Platform Integration (now SAP Integration Suite on BTP) has become the backbone for connecting ECC with satellite systems — CRM, SuccessFactors, Ariba, third-party APIs, banks, EDI partners, and more. A typical enterprise tenant can house **dozens of integration packages** containing **hundreds of integration flows (iFlows)**, each with its own externalized configurations, value mappings, and runtime deployment status.

When the time comes to migrate from ECC to S/4HANA, every single one of these integrations must be assessed:

- Which iFlows are still actively deployed?
- Which ones reference ECC-specific endpoints, BAPIs, or IDocs that will change in S/4HANA?
- What externalized parameters (URLs, credentials, paths) need to be updated?
- Which value mappings need to be revised for S/4HANA's simplified data model?
- Which integrations are in error state and may already be broken?

Answering these questions manually — by clicking through the SAP Integration Suite web UI, one artifact at a time — is tedious, error-prone, and simply does not scale.

**That is exactly the problem this tool solves.**

---

## Introducing: SAP CPI Artifact Extractor

The **SAP CPI Artifact Extractor** is a lightweight, open-source JavaFX desktop application that connects to your SAP Integration Suite tenant via the OData API, extracts comprehensive metadata about every artifact, and exports it into structured formats (Excel, CSV, JSON) — all in a single click.

Think of it as a **full X-ray of your CPI tenant** that gives you the visibility you need to plan, assess, and execute your integration migration with confidence.

### What It Extracts

| Category | What You Get |
|---|---|
| **Integration Packages** | ID, name, description, version, vendor, mode, creation/modification metadata, associated products and keywords |
| **Integration Flows** | ID, name, description, package association, version, sender/receiver systems, creation/modification history |
| **Externalized Configurations** | Every parameter key-value pair for every iFlow — URLs, paths, credentials references, client numbers, system addresses |
| **Value Mappings** | All value mapping artifacts with versioning and package association |
| **Runtime Status** | Deployment status (STARTED, ERROR, STOPPED), deployed version vs. design-time version, deployer identity, error information |

### How It Works

1. **Connect** — Enter your CPI tenant URL and authenticate via OAuth2 (client credentials) or Basic Authentication.
2. **Select** — Choose which artifact types to extract using simple checkboxes.
3. **Extract & Export** — Click the button. The tool calls the CPI OData APIs, handles pagination, retries on transient errors, and collects everything.
4. **Review** — Browse the results in six organized tabs directly in the application: Summary, Packages, Flows, Value Maps, Configurations, and Runtime.
5. **Export** — Get your data in Excel (.xlsx with multiple sheets, color-coded status, auto-filters), CSV (one file per entity), JSON (full nested structure), or all three simultaneously.

![UI Overview](docs/ui-screenshot.png)

---

## Why This Tool Is Critical During an ECC-to-S/4HANA Migration

### 1. Complete Integration Inventory in Minutes, Not Weeks

In a migration project, the first task is always: **"What do we have?"** Integration architects typically spend weeks manually cataloging iFlows, documenting configurations, and mapping dependencies. With this tool, you get a complete, exportable inventory of your entire CPI tenant in under five minutes.

The Excel export alone gives you a ready-made artifact register with separate sheets for packages, flows, configurations, value mappings, and runtime status — exactly what your migration project manager is asking for.

### 2. Identify ECC-Specific Configurations That Must Change

When you move from ECC to S/4HANA, endpoint URLs change, BAPI names change, IDoc types may be deprecated, and OData service paths are restructured. The **Configurations** extract is invaluable here — it pulls every externalized parameter from every iFlow, letting you search across your entire landscape for:

- ECC hostnames and ports that need to point to S/4HANA
- BAPI function module references (e.g., `BAPI_SALESORDER_CREATEFROMDAT2` vs. S/4HANA equivalents)
- RFC destinations that need reconfiguration
- SAP Client numbers that may change
- Credential store references that need updating

Instead of opening each iFlow individually in the web UI, you can filter and sort hundreds of configuration parameters in a single Excel sheet.

### 3. Spot Broken Integrations Before They Become Migration Blockers

The **Runtime Status** extraction identifies every artifact in ERROR state along with the actual error message. During a migration, you do not want to discover halfway through cutover that 15 iFlows have been silently failing for months. This tool gives you an immediate health check:

- **STARTED** — Running normally (shown in green in the Excel export)
- **ERROR** — Failed deployment with error details (shown in red)
- **Not deployed** — Design-time only, may be obsolete

This data feeds directly into your migration test plan — you know exactly which integrations to validate first.

### 4. Version Drift Detection

The tool captures both the **design-time version** and the **deployed runtime version** for each artifact. During active development and migration sprints, it is common for developers to modify iFlows without redeploying them. This version drift can lead to nasty surprises in production. The side-by-side version comparison lets you flag discrepancies immediately.

### 5. Stakeholder Reporting and Governance

Migration projects involve many stakeholders — project managers, integration architects, security teams, and business process owners. The structured Excel output with auto-filters, color coding, and summary statistics makes it easy to:

- Report progress to steering committees
- Hand off integration inventories to testing teams
- Provide security teams with a list of all credential references and endpoint configurations
- Document the as-is state for audit and compliance purposes

### 6. Multi-Tenant and Multi-Landscape Support

Many organizations maintain separate CPI tenants for development, quality, and production. You can run the tool against each tenant and compare the outputs to identify:

- Artifacts missing from higher environments (not transported)
- Configuration differences between landscapes (e.g., DEV pointing to sandbox, PROD pointing to live systems)
- Version inconsistencies across the transport pipeline

Simply save a configuration file for each tenant and switch between them using the **Load Config** feature.

---

## A Real-World Migration Scenario

Consider a mid-sized enterprise running SAP ECC 6.0 with 8 integration packages and 45 iFlows on SAP CPI, covering:

- **SuccessFactors Employee Central** integration for HR master data replication
- **Ariba** procurement integration for purchase orders and invoices
- **Bank statement imports** (BAI format) from file servers
- **C4C (Sales Cloud)** integration for consumption and rate category data
- **Custom EDI** integrations with trading partners

The S/4HANA migration team needs to:

1. **Catalog** all 45 iFlows and understand which packages they belong to.
2. **Assess** which iFlows call ECC-specific services that will change in S/4HANA.
3. **Identify** externalized parameters (host, port, client, service paths) that need updating.
4. **Check** which iFlows are actively running and which are dormant or broken.
5. **Plan** the integration testing sequence for the migration cutover window.

Without a tool like this, steps 1-4 alone could take a consultant 2-3 weeks of manual effort. With the **SAP CPI Artifact Extractor**, all of this data is extracted, organized, and exported in a single run — giving the team a comprehensive baseline to plan from on Day 1.

---

## Technical Details

### Prerequisites

- **Java 17** or higher
- The tool is distributed as a single executable JAR file (~30 MB) — no installation required

### Authentication Setup

The tool supports two authentication methods:

**OAuth2 (Recommended for BTP):**
Create a service instance of the Process Integration Runtime service in your BTP subaccount with the `api` plan. Bind it to get the `clientid`, `clientsecret`, and `tokenurl`. Enter these in the tool's connection panel.

**Basic Authentication:**
Use your SAP CPI user credentials directly. Suitable for trial accounts or tenants with basic auth enabled.

### Running the Tool

```bash
java -jar cpi-artifact-extractor-ui-1.0.0.jar
```

That's it. The JavaFX UI launches, and you're ready to connect.

### Configuration Persistence

You can save and load connection settings as `.properties` files, making it easy to switch between tenants or share (sanitized) configurations with team members. Environment variables are also supported for CI/CD or scripted scenarios.

---

## Key Features at a Glance

| Feature | Description |
|---|---|
| **One-Click Extraction** | Connect, select options, click Extract — complete tenant metadata in minutes |
| **6 Tabbed Result Views** | Summary, Packages, Flows, Value Maps, Configurations, Runtime — all browsable in the UI |
| **Excel Export** | Multi-sheet workbook with color-coded status, auto-filters, and freeze panes |
| **CSV Export** | Separate files per entity type for easy import into other tools |
| **JSON Export** | Full nested data structure for programmatic processing |
| **OAuth2 & Basic Auth** | Supports both BTP-standard OAuth2 and legacy basic authentication |
| **Selective Extraction** | Choose exactly which artifact types to extract via checkboxes |
| **Retry & Error Handling** | Automatic retries with exponential backoff for transient API errors |
| **Real-Time Logging** | Watch API calls and progress in the built-in log panel |
| **Config Save/Load** | Persist and reload connection settings for multiple tenants |
| **Portable** | Single JAR file, no installation, runs on Windows, macOS, and Linux |

---

## What's Next?

Future enhancements under consideration:

- **Diff mode** — Compare two extraction snapshots to identify what changed between migration phases
- **Data Store and Variable extraction** — Capture persisted runtime data
- **Message log retrieval** — Pull recent processing logs for error analysis
- **Bulk configuration update** — Push updated parameter values back to CPI (e.g., swap ECC URLs for S/4HANA URLs across all iFlows)
- **PDF report generation** — Executive-ready migration assessment reports

---

## Conclusion

Migrating from SAP ECC to S/4HANA is a transformational journey, and your integration landscape on SAP CPI is a critical part of that journey. Without clear visibility into what you have, what's running, and what needs to change, you're flying blind.

The **SAP CPI Artifact Extractor** gives integration architects and migration teams the structured, exportable, and comprehensive view they need — turning what used to be weeks of manual documentation into a five-minute automated extraction.

Whether you're in the assessment phase, actively migrating, or performing post-go-live validation, this tool belongs in your migration toolkit.

---

*Have questions or feedback? Feel free to reach out in the comments below.*
