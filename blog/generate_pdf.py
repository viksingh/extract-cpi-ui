"""Generate the SAP CPI blog post PDF using fpdf2."""
from fpdf import FPDF
from fpdf.enums import XPos, YPos

SAP_BLUE      = (0,   112, 242)
SAP_DARK_BLUE = (0,   61,  122)
SAP_TEAL      = (15,  130, 143)
WHITE         = (255, 255, 255)
BLACK         = (29,   45,  62)
TABLE_STRIPE  = (234, 241, 251)
NOTE_BG       = (255, 248, 225)
NOTE_BORDER   = (224, 123,   0)
SAP_MUTED     = (85,  107, 130)

class BlogPDF(FPDF):
    def footer(self):
        self.set_y(-16)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(*SAP_MUTED)
        self.cell(0, 8,
                  f"SAP CPI Artifact Extractor  |  github.com/viksingh/extract-cpi-ui  |  Page {self.page_no()}",
                  align="C")

    def h2(self, text):
        self.ln(8)
        self.set_fill_color(*SAP_DARK_BLUE)
        self.set_text_color(*WHITE)
        self.set_font("Helvetica", "B", 12)
        self.cell(0, 8, "  " + text, fill=True, new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        self.set_text_color(*BLACK)
        self.ln(3)

    def h3(self, text):
        self.ln(4)
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(*SAP_TEAL)
        self.cell(0, 6, text, new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        self.set_text_color(*BLACK)
        self.ln(1)

    def para(self, text):
        self.set_font("Helvetica", "", 10)
        self.set_text_color(*BLACK)
        self.multi_cell(0, 5.5, text)
        self.ln(3)

    def bullet(self, label, text=""):
        self.set_font("Helvetica", "", 10)
        self.set_text_color(*BLACK)
        x = self.get_x()
        self.set_x(self.l_margin + 5)
        self.cell(3, 5.5, "-")
        if label and text:
            self.set_font("Helvetica", "B", 10)
            self.cell(0, 5.5, label + " ", new_x=XPos.LMARGIN, new_y=YPos.NEXT)
            self.set_x(self.l_margin + 8)
            self.set_font("Helvetica", "", 10)
            self.multi_cell(0, 5.5, text)
        else:
            self.set_font("Helvetica", "", 10)
            self.multi_cell(0, 5.5, label)
        self.ln(1)

    def note(self, text):
        self.set_fill_color(*NOTE_BG)
        self.set_draw_color(*NOTE_BORDER)
        self.set_line_width(0.6)
        self.set_font("Helvetica", "I", 10)
        self.set_text_color(80, 40, 0)
        # left bar
        y = self.get_y()
        self.set_fill_color(*NOTE_BORDER)
        self.rect(self.l_margin, y, 2.5, 14, "F")
        self.set_fill_color(*NOTE_BG)
        self.set_x(self.l_margin + 5)
        self.multi_cell(0, 5.5, text, fill=True)
        self.set_text_color(*BLACK)
        self.ln(4)

    def github_box(self, url):
        self.set_fill_color(*SAP_DARK_BLUE)
        self.set_text_color(*WHITE)
        self.set_font("Helvetica", "B", 10)
        self.cell(0, 9, "  GitHub: " + url, fill=True, new_x=XPos.LMARGIN, new_y=YPos.NEXT)
        self.set_text_color(*BLACK)
        self.ln(4)

    def table(self, headers, rows, col_widths=None):
        page_w = self.w - self.l_margin - self.r_margin
        if col_widths is None:
            col_widths = [page_w / len(headers)] * len(headers)
        # header
        self.set_fill_color(*SAP_DARK_BLUE)
        self.set_text_color(*WHITE)
        self.set_font("Helvetica", "B", 8.5)
        for i, h in enumerate(headers):
            self.cell(col_widths[i], 7, h, border=0, fill=True)
        self.ln()
        # rows
        self.set_font("Helvetica", "", 8.5)
        for ri, row in enumerate(rows):
            self.set_fill_color(*TABLE_STRIPE if ri % 2 == 0 else WHITE)
            self.set_text_color(*BLACK)
            y_start = self.get_y()
            x_start = self.l_margin
            max_h = 5
            for ci, cell in enumerate(row):
                self.set_xy(x_start + sum(col_widths[:ci]), y_start)
                self.multi_cell(col_widths[ci], 5, cell, fill=True)
                max_h = max(max_h, self.get_y() - y_start)
            self.set_y(y_start + max_h)
        self.ln(4)

    def screenshot(self, path, caption):
        """Embed a screenshot with a numbered caption."""
        import os
        if not os.path.exists(path):
            return
        # Keep image on same page if possible; otherwise break
        available = self.h - self.get_y() - self.b_margin
        page_w = self.w - self.l_margin - self.r_margin
        # Estimate rendered height (max 90mm)
        target_w = page_w
        target_h = min(90, available - 12)
        if target_h < 30:
            self.add_page()
            target_h = 90
        self.ln(3)
        self.image(path, x=self.l_margin, w=target_w, h=0)
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(*SAP_MUTED)
        self.multi_cell(0, 4.5, caption, align="C")
        self.set_text_color(*BLACK)
        self.ln(5)

    def code(self, text):
        self.set_fill_color(240, 244, 249)
        self.set_font("Courier", "", 9)
        self.set_text_color(30, 30, 30)
        self.multi_cell(0, 5, text, border="L", fill=True)
        self.set_text_color(*BLACK)
        self.ln(4)

    def step(self, n, text):
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(*SAP_BLUE)
        self.set_x(self.l_margin + 4)
        self.cell(8, 6, str(n) + ".")
        self.set_font("Helvetica", "", 10)
        self.set_text_color(*BLACK)
        self.multi_cell(0, 6, text)
        self.ln(1)


import os as _os
IMAGES = r"C:\Users\vikas\OneDrive\Documents\proj\BTPISExtract\extract-cpi-ui\blog\images"
def img(name): return _os.path.join(IMAGES, name)

# ─── BUILD ─────────────────────────────────────────────────────────────────
pdf = BlogPDF()
pdf.set_auto_page_break(auto=True, margin=18)
pdf.add_page()
pdf.set_margins(20, 20, 20)

# Cover banner
pdf.set_fill_color(*SAP_DARK_BLUE)
pdf.rect(0, 0, pdf.w, 48, "F")
pdf.set_fill_color(*SAP_BLUE)
pdf.rect(0, 40, pdf.w, 10, "F")
pdf.set_fill_color(*SAP_TEAL)
pdf.rect(0, 48, pdf.w, 4, "F")

pdf.set_y(7)
pdf.set_font("Helvetica", "", 7.5)
pdf.set_text_color(*WHITE)
pdf.cell(0, 4, "SAP COMMUNITY  |  INTEGRATION  |  CLOUD PLATFORM INTEGRATION", align="C",
         new_x=XPos.LMARGIN, new_y=YPos.NEXT)
pdf.ln(3)
pdf.set_font("Helvetica", "B", 14)
pdf.multi_cell(0, 8,
    "SAP CPI Artifact Extractor\nTracking Your Integration Landscape During ECC to S/4HANA Migration",
    align="C")
pdf.set_y(42)
pdf.set_font("Helvetica", "", 8)
pdf.set_text_color(200, 225, 255)
pdf.cell(0, 5, "February 2026  |  github.com/viksingh/extract-cpi-ui", align="C",
         new_x=XPos.LMARGIN, new_y=YPos.NEXT)
pdf.set_y(58)
pdf.set_text_color(*BLACK)

# Intro
pdf.set_fill_color(232, 241, 251)
pdf.set_font("Helvetica", "", 10)
pdf.set_text_color(0, 61, 122)
pdf.multi_cell(0, 5.5,
    "I am currently working on an ECC to S/4HANA migration. We have a number of migration cycles "
    "and retrofit activities ahead of us, and one of the first things I needed was visibility into "
    "what was deployed on the CPI tenant and how it was changing over time. SAP does not provide "
    "a built-in way to extract this information in bulk. Let me walk you through a tool I built "
    "to support these cycles and retrofit activities.",
    fill=True)
pdf.set_text_color(*BLACK)
pdf.ln(4)
pdf.github_box("https://github.com/viksingh/extract-cpi-ui")

# ── Section 1 ───────────────────────────────────────────────────────────────
pdf.h2("Background - Tools Are Not Optional at Scale")
pdf.para("This is not the first time I have needed to build tooling to support a migration.")
pdf.para(
    "I have been working on SAP integration projects for a number of years. In previous projects "
    "on PI/PO, when you have tens of thousands of interfaces, manual processes simply do not scale. "
    "I built a number of tools back then to support migrations:"
)
pdf.bullet("Communication channel extraction",
           "bulk export of all sender and receiver channels so the migration team could understand "
           "what existed without logging into each channel one at a time")
pdf.bullet("Module parameter updates",
           "programmatic bulk changes to adapter module configurations across hundreds of channels at once")
pdf.bullet("SLD object creation and updates",
           "automated management of System Landscape Directory entries for technical and business "
           "systems as new systems were introduced during each cutover wave")
pdf.bullet("Mapping information extraction",
           "export of all mapping objects with their associated interfaces so we knew what needed to be rebuilt")
pdf.ln(2)
pdf.note(
    "The teams that had tooling finished cutover verification in hours. The teams that did not "
    "were still working through screens at 3am on go-live night."
)
pdf.para(
    "Now I am on a CPI migration and facing the same problem. The platform has changed - it is "
    "OData and OAuth2 rather than XI protocol and basic auth - but the need is the same. With "
    "multiple cycles and retrofit activities planned, I need to know what is deployed, track how "
    "it changes between waves, and verify the state at each cutover."
)

# ── Section 2 ───────────────────────────────────────────────────────────────
pdf.h2("The Problem")
pdf.para(
    "SAP CPI's Design Time lets you browse packages and iFlows one at a time. There is no native "
    "way to export a full inventory of all artifacts - packages, flows, value mappings, "
    "configurations, runtime status - into something you can actually analyse."
)
pdf.para("For a large tenant with hundreds of iFlows across dozens of packages, this becomes a real problem when you need to:")
for item in [
    "Take a baseline snapshot before migration starts",
    "See what has changed between migration waves",
    "Verify at cutover that the right flows are deployed and running",
    "Hand something to a functional team who does not have CPI access",
]:
    pdf.bullet(item)
pdf.ln(2)

# ── Section 3 ───────────────────────────────────────────────────────────────
pdf.h2("The Tool - extract-cpi-ui")
pdf.para(
    "extract-cpi-ui is a JavaFX desktop application that connects to your CPI tenant via the "
    "OData v1 API and extracts a complete inventory in one operation. It runs on Java 17+. "
    "No SAP GUI. No additional BTP subscription beyond the CPI credentials you already have."
)
pdf.para(
    "The tool was built over four months of weekend development from November 2025 through "
    "February 2026. All API interactions are read-only HTTP GET requests against the OData v1 "
    "endpoints. The only exception is a single POST request used to obtain an OAuth2 access "
    "token via the client credentials grant -- the tool never writes to or modifies your "
    "CPI tenant."
)
pdf.screenshot(img("01_initial_launch.png"),
    "Figure 1: The application on first launch - connection settings, extraction options, and export format all in one window.")
pdf.h3("What It Extracts")
pdf.table(
    ["Artifact Type", "Key Fields"],
    [
        ["Integration Packages",
         "ID, Name, Version, Vendor, Mode, Created By, Creation Date, Modified By, Modified Date"],
        ["Integration Flows",
         "ID, Name, Package, Version, Sender, Receiver, Created By, Created At, Modified By, Modified At, Runtime Status"],
        ["Value Mappings",
         "ID, Name, Package, Version, Created By, Created At, Modified By, Modified At, Runtime Status"],
        ["Configurations",   "Artifact ID, Parameter Key, Parameter Value, Data Type"],
        ["Runtime Artifacts","Artifact ID, Name, Type, Status (STARTED/ERROR/UNDEPLOYED), Deployed By, Deployed On"],
    ],
    col_widths=[44, 126]
)
pdf.para(
    "Authentication supports OAuth2 client credentials (recommended for CF) and Basic Auth for "
    "Neo or service users. All dates are displayed as yyyy-MM-dd HH:mm:ss rather than the raw "
    "SAP epoch format (/Date(1234567890000)/) that comes back from the API."
)
pdf.screenshot(img("05_packages_tab.png"),
    "Figure 2: Packages tab - all integration packages with version, vendor, and date information.")
pdf.screenshot(img("06_flows_tab.png"),
    "Figure 3: Flows tab - integration flows with package grouping, version, and runtime status.")
pdf.screenshot(img("08_runtime_tab.png"),
    "Figure 4: Runtime tab - STARTED / ERROR / UNDEPLOYED status for cutover verification.")
pdf.ln(1)

# ── Section 4 ───────────────────────────────────────────────────────────────
pdf.h2("Date Filter")
pdf.para(
    "One thing I needed specifically for the migration was the ability to filter by date. If I "
    "extract the full tenant today and again in two months, I want to see what changed in that "
    "period rather than looking at 550 iFlows again from scratch."
)
pdf.para("The date filter lets you narrow the results to artifacts created or modified on or after a date you choose. Three modes:")
pdf.table(
    ["Mode", "What It Keeps", "Best For"],
    [
        ["Modified since",            "Artifacts touched on or after the chosen date", "Tracking change between migration waves"],
        ["Created since",             "Net-new artifacts created on or after the date", "Confirming what was built during a wave"],
        ["Created or modified since", "Either created or modified on or after the date", "Broadest view of all activity"],
    ],
    col_widths=[44, 82, 44]
)
pdf.screenshot(img("10_date_filter_enabled.png"),
    "Figure 5: Date filter enabled - choose a mode and a date to narrow results to a specific migration wave.")
pdf.para(
    "The filter applies to packages, iFlows, and value mappings. A package is kept if it passes "
    "the filter itself or if any of its child artifacts pass."
)
pdf.para(
    "The filter works on both live extractions and previously saved JSON snapshots. You can take "
    "a snapshot today, save it to disk, and reload it months later with a different date filter "
    "without touching the API again. This was important to me because production API access is "
    "sometimes restricted outside of maintenance windows."
)

# ── Section 5 ───────────────────────────────────────────────────────────────
pdf.h2("Export Formats")
pdf.para("The tool exports to three formats. I added all three because they each serve a different purpose.")

pdf.h3("Excel")
pdf.para(
    "A multi-sheet workbook with one sheet per artifact type. This is what I use to share with "
    "functional consultants and project managers who need to understand the integration landscape "
    "but have no CPI access. It is pivot-table-ready and you can filter and sort it without any "
    "technical knowledge."
)

pdf.h3("CSV")
pdf.para(
    "One file per artifact type. The reason I included CSV alongside Excel is that CPI artifact "
    "names and descriptions sometimes contain special characters - em dashes, Unicode in "
    "description fields, vendor-provided text in non-ASCII encodings. These can silently corrupt "
    "an Excel cell. CSV with proper quoting handles this safely and you can import it into any "
    "tool or process it on the command line."
)

pdf.h3("JSON")
pdf.para(
    "A single file containing the complete extraction result. Every field the API returned is "
    "preserved. This is the format I care most about for the migration. The JSON file is a "
    "point-in-time snapshot of the tenant. I can reload it in the tool at any time and apply "
    "any date filter to the historical data."
)
pdf.para(
    "One thing I have learned on migration projects is that the integration landscape is dynamic. "
    "Flows do not stay still while you migrate. Developers keep changing things, fixing bugs, "
    "adjusting configurations. Something you retrofitted in Wave 1 may have been modified again "
    "by Wave 3. Without snapshots you have no way to know what changed after you last looked at "
    "it, and retrofits done without this visibility tend to be incomplete or based on stale information."
)
pdf.para(
    "By taking snapshots regularly throughout the migration, you have a record of the landscape "
    "at each point in time. When a retrofit task comes up you can compare the current state "
    "against the snapshot from when the retrofit was originally planned and see if anything has "
    "changed since. This makes the retrofit work more reliable and reduces the risk of missing "
    "changes that happened in the background."
)
pdf.para("I have plans to build a diff tool that takes two JSON snapshots and identifies:")
for item in [
    "Flows that exist in the newer snapshot but not the older one - net-new, need to be built or verified on the target",
    "Flows in both but with different ModifiedAt dates - changed, configurations may need retrofitting",
    "Flows in the older snapshot but not the newer - removed, confirm this was intentional",
]:
    pdf.bullet(item)
pdf.ln(2)
pdf.para(
    "By keeping a library of JSON snapshots taken at each migration wave, the diff tool will "
    "have everything it needs to generate a change log automatically."
)

# ── Section 6 ───────────────────────────────────────────────────────────────
pdf.h2("Migration Workflow")
pdf.para("The way I use this in the current project:")

pdf.h3("Before migration starts")
pdf.para("Extract the full tenant. Save the JSON. Export to Excel and share with the workstream leads so everyone knows what exists. This is the baseline.")

pdf.h3("After each migration wave")
pdf.para("Extract with 'Modified since' set to the wave start date. This shows only what was touched during that wave. Export to Excel for review. Save the JSON for the diff tool.")

pdf.h3("Pre go-live")
pdf.para(
    "Extract the full tenant again with no date filter. Check the runtime status sheet - everything "
    "that should be running should show as STARTED with no ERROR status. This is the cutover "
    "verification step that would otherwise require someone clicking through each iFlow manually."
)

pdf.h3("Post go-live")
pdf.para(
    "Save the go-live JSON snapshot. When the diff tool is ready, this becomes the baseline "
    "for tracking any hypercare changes."
)

# ── Section 7 ───────────────────────────────────────────────────────────────
pdf.h2("Getting Started")
pdf.para("You need Java 17 or later and OAuth2 credentials or a Basic Auth service user for your CPI tenant.")
pdf.para("Download the JAR from the releases page and run it:")
pdf.code("java -jar cpi-artifact-extractor-ui-1.0.0.jar")
pdf.para("No installation. The UI opens as a desktop window.")
for i, s in enumerate([
    "Enter your tenant URL",
    "Select OAuth2 and enter your token URL, client ID, and client secret",
    "Choose an export format - Excel for a first run",
    "Set an output directory",
    "Optionally enable the date filter and choose a date",
    "Click Extract & Export",
], 1):
    pdf.step(i, s)
pdf.ln(3)
pdf.screenshot(img("02_config_loaded.png"),
    "Figure 6: Credentials loaded via Load Config File - all fields populated in one click.")
pdf.screenshot(img("03_extracting.png"),
    "Figure 7: Extraction running - OAuth2 token obtained and packages being fetched from the OData API.")
pdf.screenshot(img("04_extraction_complete.png"),
    "Figure 8: Extraction complete - Summary tab with artifact counts and export confirmation.")
pdf.para(
    "Use Save Config to write your settings to a properties file. Load Config File restores "
    "everything in one click including the date filter settings. To reload a saved snapshot: "
    "click Load Snapshot..., pick the JSON file. If you have the date filter enabled it applies "
    "automatically without hitting the API."
)

# ── Section 8 ───────────────────────────────────────────────────────────────
pdf.h2("What Is Next")
pdf.para(
    "This is the first tool in a broader CPI toolset I am building to support the current ECC "
    "to S/4HANA migration. With multiple cycles still ahead and retrofit activities running in "
    "parallel, the tooling needs to keep pace with the project. The immediate next step is the "
    "snapshot diff tool mentioned above - a direct requirement for comparing the landscape state "
    "between cycles. After that I have plans for cutover verification reports and a few other "
    "things driven by what each upcoming cycle surfaces."
)
pdf.github_box("https://github.com/viksingh/extract-cpi-ui")

pdf.ln(4)
pdf.set_font("Helvetica", "I", 10)
pdf.set_text_color(*SAP_MUTED)
pdf.multi_cell(0, 5.5,
    "If you have faced similar challenges managing a CPI tenant during a migration, or if you "
    "have built tools of your own for this, I would be interested to hear about it in the comments.")

# Output
out = "C:/Users/vikas/OneDrive/Documents/proj/BTPISExtract/extract-cpi-ui/blog/sap-cpi-extractor-blog.pdf"
pdf.output(out)
print(f"PDF written to: {out}")
