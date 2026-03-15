package com.sakiv.cpi.extractor.ui;

import com.sakiv.cpi.extractor.config.CpiConfiguration;
import com.sakiv.cpi.extractor.export.CsvExporter;
import com.sakiv.cpi.extractor.export.ExcelExporter;
import com.sakiv.cpi.extractor.export.JsonExporter;
import com.sakiv.cpi.extractor.model.*;
import com.sakiv.cpi.extractor.service.CpiApiService;
import com.sakiv.cpi.extractor.service.CpiHttpClient;
import com.sakiv.cpi.extractor.service.CpiHttpClient.ApiCallRecord;
import com.sakiv.cpi.extractor.service.ProfileManager;
import com.sakiv.cpi.extractor.service.SnapshotLoader;
import com.sakiv.cpi.extractor.util.DateFilterUtil;
import com.sakiv.cpi.extractor.util.LocaleManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // Original unfiltered data — stored after each load/extraction so the filter
    // can be re-applied live when the user changes filter settings without reloading.
    private ExtractionResult currentResult;
    private List<IntegrationPackage> origPackages;
    private List<IntegrationFlow> origAllFlows;
    private List<ValueMapping> origAllVMs;
    private List<RuntimeArtifact> origRuntimeArtifacts;
    private final Map<String, List<IntegrationFlow>> origPkgFlows = new HashMap<>();
    private final Map<String, List<ValueMapping>> origPkgVMs = new HashMap<>();

    // i18n
    private ResourceBundle bundle;

    // Language selector
    @FXML private ComboBox<String> languageCombo;

    // Connection Profiles (E9)
    private final ProfileManager profileManager = new ProfileManager();

    // Connection fields
    @FXML private ComboBox<String> profileCombo;
    @FXML private TextField tenantUrlField;
    @FXML private ComboBox<String> authTypeCombo;
    @FXML private Label oauthTokenUrlLabel;
    @FXML private TextField oauthTokenUrlField;
    @FXML private Label oauthClientIdLabel;
    @FXML private TextField oauthClientIdField;
    @FXML private Label oauthClientSecretLabel;
    @FXML private PasswordField oauthClientSecretField;
    @FXML private Label basicUsernameLabel;
    @FXML private TextField basicUsernameField;
    @FXML private Label basicPasswordLabel;
    @FXML private PasswordField basicPasswordField;

    // Extraction checkboxes
    @FXML private CheckBox extractPackagesCb;
    @FXML private CheckBox extractFlowsCb;
    @FXML private CheckBox extractValueMappingsCb;
    @FXML private CheckBox extractConfigurationsCb;
    @FXML private CheckBox extractRuntimeCb;
    @FXML private CheckBox extractMessageLogsCb;

    // Deep extraction toggle
    @FXML private CheckBox deepExtractionCb;
    @FXML private VBox deepExtractionControls;

    // Date filter
    @FXML private CheckBox dateFilterEnabledCb;
    @FXML private HBox dateFilterControls;
    @FXML private ComboBox<DateFilterUtil.FilterMode> dateFilterModeCombo;
    @FXML private DatePicker sinceDatePicker;

    // Package filter (checkbox-based)
    @FXML private VBox packageCheckboxContainer;
    @FXML private CheckBox selectAllPackagesCb;
    @FXML private TextField packageSearchField;
    private boolean updatingPackageCheckboxes; // guard against recursive listener calls

    // Export settings
    @FXML private ComboBox<String> exportFormatCombo;
    @FXML private TextField outputDirField;
    @FXML private TextField filenamePrefixField;
    @FXML private CheckBox autoSnapshotCb;

    // Results
    @FXML private TabPane resultsTabPane;
    @FXML private TextArea summaryTextArea;
    @FXML private TableView<IntegrationPackage> packagesTable;
    @FXML private TableView<IntegrationFlow> flowsTable;
    @FXML private TableView<ValueMapping> valueMapsTable;
    @FXML private TableView<ConfigRow> configsTable;
    @FXML private TableView<RuntimeArtifact> runtimeTable;
    @FXML private TableView<AdapterRow> adaptersTable;
    @FXML private TableView<IFlowUsageRow> iflowUsageTable;
    @FXML private TableView<CredentialRow> credentialsTable;
    @FXML private TableView<EccEndpointRow> eccEndpointsTable;
    @FXML private TableView<FlowChainRow> flowChainsTable;
    @FXML private TableView<ApiCallRow> apiCallsTable;

    // Package filter pane
    @FXML private TitledPane packageFilterPane;

    // Log + progress
    @FXML private TextArea logTextArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button extractButton;
    @FXML private Button loadSnapshotBtn;
    @FXML private Button fetchPackagesBtn;

    // Pre-fetched packages from "Fetch Packages" step
    private List<IntegrationPackage> fetchedPackages;

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    public void initialize() {
        // i18n
        bundle = LocaleManager.getBundle();

        // Language selector
        languageCombo.setItems(FXCollections.observableArrayList("English", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439"));
        languageCombo.setValue("ru".equals(LocaleManager.getCurrentLocale().getLanguage()) ? "\u0420\u0443\u0441\u0441\u043a\u0438\u0439" : "English");

        // Auth type combo
        authTypeCombo.setItems(FXCollections.observableArrayList("OAuth2", "Basic"));
        authTypeCombo.setValue("OAuth2");

        // Export format combo
        exportFormatCombo.setItems(FXCollections.observableArrayList(
                bundle.getString("export.format.excel"),
                bundle.getString("export.format.csv"),
                bundle.getString("export.format.json"),
                bundle.getString("export.format.all")));
        exportFormatCombo.setValue(bundle.getString("export.format.excel"));

        // Date filter combo
        dateFilterModeCombo.setItems(FXCollections.observableArrayList(DateFilterUtil.FilterMode.values()));
        dateFilterModeCombo.setValue(DateFilterUtil.FilterMode.MODIFIED_SINCE);

        // Wire up log appender
        TextAreaLogAppender.setTextArea(logTextArea);

        // Initialize table columns
        initPackagesTable();
        initFlowsTable();
        initValueMapsTable();
        initConfigsTable();
        initRuntimeTable();
        initAdaptersTable();
        initIflowUsageTable();
        initCredentialsTable();
        initEccEndpointsTable();
        initFlowChainsTable();
        initApiCallsTable();

        // Re-apply filter automatically whenever filter settings change after data is loaded
        dateFilterEnabledCb.selectedProperty().addListener((obs, old, val) -> reapplyFilter());
        sinceDatePicker.valueProperty().addListener((obs, old, val) -> reapplyFilter());
        dateFilterModeCombo.valueProperty().addListener((obs, old, val) -> reapplyFilter());

        // Select All checkbox toggles all visible package checkboxes
        selectAllPackagesCb.selectedProperty().addListener((obs, old, val) -> {
            if (updatingPackageCheckboxes) return;
            updatingPackageCheckboxes = true;
            for (var node : packageCheckboxContainer.getChildren()) {
                if (node instanceof CheckBox cb && cb.isVisible()) cb.setSelected(val);
            }
            updatingPackageCheckboxes = false;
            reapplyFilter();
        });

        // Package search field filters the checkbox list
        packageSearchField.textProperty().addListener((obs, old, val) -> {
            String filter = val != null ? val.toLowerCase().trim() : "";
            for (var node : packageCheckboxContainer.getChildren()) {
                if (node instanceof CheckBox cb) {
                    boolean match = filter.isEmpty() || cb.getText().toLowerCase().contains(filter);
                    cb.setVisible(match);
                    cb.setManaged(match);
                }
            }
        });

        // Load connection profiles (E9)
        loadProfileCombo();
    }

    // =========================================================================
    // Language Selection
    // =========================================================================

    @FXML
    private void onLanguageChanged() {
        String selected = languageCombo.getValue();
        if (selected == null) return;
        Locale newLocale = "\u0420\u0443\u0441\u0441\u043a\u0438\u0439".equals(selected) ? Locale.forLanguageTag("ru") : Locale.ENGLISH;
        if (!newLocale.equals(LocaleManager.getCurrentLocale())) {
            LocaleManager.setLocale(newLocale);
            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle(bundle.getString("language.restart.title"));
            info.setHeaderText(null);
            info.setContentText(LocaleManager.getString("language.restart"));
            info.showAndWait();
        }
    }

    private String msg(String key) {
        return bundle.getString(key);
    }

    private String msg(String key, Object... args) {
        return MessageFormat.format(bundle.getString(key), args);
    }

    // =========================================================================
    // Connection Profiles (E9)
    // =========================================================================

    private void loadProfileCombo() {
        List<ConnectionProfile> profiles = profileManager.loadProfiles();
        List<String> names = new ArrayList<>();
        names.add(""); // empty = no profile selected
        for (ConnectionProfile p : profiles) {
            names.add(p.getName());
        }
        String currentSelection = profileCombo.getValue();
        profileCombo.setItems(FXCollections.observableArrayList(names));
        if (currentSelection != null && names.contains(currentSelection)) {
            profileCombo.setValue(currentSelection);
        }
    }

    @FXML
    private void onProfileSelected() {
        String selected = profileCombo.getValue();
        if (selected == null || selected.isEmpty()) return;

        List<ConnectionProfile> profiles = profileManager.loadProfiles();
        for (ConnectionProfile p : profiles) {
            if (p.getName().equals(selected)) {
                applyProfile(p);
                appendLog(msg("profile.log.loaded", selected));
                return;
            }
        }
    }

    private void applyProfile(ConnectionProfile p) {
        if (p.getTenantUrl() != null) tenantUrlField.setText(p.getTenantUrl());
        if (p.getAuthType() != null) {
            authTypeCombo.setValue("basic".equalsIgnoreCase(p.getAuthType()) ? "Basic" : "OAuth2");
            onAuthTypeChanged();
        }
        if (p.getOauthTokenUrl() != null) oauthTokenUrlField.setText(p.getOauthTokenUrl());
        if (p.getOauthClientId() != null) oauthClientIdField.setText(p.getOauthClientId());
        if (p.getOauthClientSecret() != null) oauthClientSecretField.setText(p.getOauthClientSecret());
        if (p.getBasicUsername() != null) basicUsernameField.setText(p.getBasicUsername());
        if (p.getBasicPassword() != null) basicPasswordField.setText(p.getBasicPassword());
        if (p.getOutputDir() != null && !p.getOutputDir().isBlank()) outputDirField.setText(p.getOutputDir());
        if (p.getFilenamePrefix() != null && !p.getFilenamePrefix().isBlank()) filenamePrefixField.setText(p.getFilenamePrefix());
    }

    @FXML
    private void onSaveProfile() {
        TextInputDialog dialog = new TextInputDialog(
                profileCombo.getValue() != null && !profileCombo.getValue().isEmpty()
                        ? profileCombo.getValue() : "");
        dialog.setTitle(msg("profile.save.dialog.title"));
        dialog.setHeaderText(msg("profile.save.dialog.header"));
        dialog.setContentText(msg("profile.save.dialog.content"));

        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                showError(msg("validation.title"), msg("profile.validation.empty"));
                return;
            }
            ConnectionProfile profile = new ConnectionProfile();
            profile.setName(name.trim());
            profile.setTenantUrl(tenantUrlField.getText().trim());
            profile.setAuthType("OAuth2".equals(authTypeCombo.getValue()) ? "oauth2" : "basic");
            profile.setOauthTokenUrl(oauthTokenUrlField.getText().trim());
            profile.setOauthClientId(oauthClientIdField.getText().trim());
            profile.setOauthClientSecret(oauthClientSecretField.getText().trim());
            profile.setBasicUsername(basicUsernameField.getText().trim());
            profile.setBasicPassword(basicPasswordField.getText().trim());
            profile.setOutputDir(outputDirField.getText().trim());
            profile.setFilenamePrefix(filenamePrefixField.getText().trim());

            profileManager.addOrUpdateProfile(profile);
            loadProfileCombo();
            profileCombo.setValue(name.trim());
            appendLog(msg("profile.log.saved", name.trim()));
        });
    }

    @FXML
    private void onDeleteProfile() {
        String selected = profileCombo.getValue();
        if (selected == null || selected.isEmpty()) {
            showError(msg("profile.noselection.title"), msg("profile.noselection.message"));
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                msg("profile.delete.dialog.confirm", selected), ButtonType.YES, ButtonType.NO);
        confirm.setTitle(msg("profile.delete.dialog.title"));
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                profileManager.deleteProfile(selected);
                loadProfileCombo();
                profileCombo.setValue("");
                appendLog(msg("profile.log.deleted", selected));
            }
        });
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onAuthTypeChanged() {
        boolean isOAuth = "OAuth2".equals(authTypeCombo.getValue());

        oauthTokenUrlLabel.setVisible(isOAuth);
        oauthTokenUrlLabel.setManaged(isOAuth);
        oauthTokenUrlField.setVisible(isOAuth);
        oauthTokenUrlField.setManaged(isOAuth);
        oauthClientIdLabel.setVisible(isOAuth);
        oauthClientIdLabel.setManaged(isOAuth);
        oauthClientIdField.setVisible(isOAuth);
        oauthClientIdField.setManaged(isOAuth);
        oauthClientSecretLabel.setVisible(isOAuth);
        oauthClientSecretLabel.setManaged(isOAuth);
        oauthClientSecretField.setVisible(isOAuth);
        oauthClientSecretField.setManaged(isOAuth);

        basicUsernameLabel.setVisible(!isOAuth);
        basicUsernameLabel.setManaged(!isOAuth);
        basicUsernameField.setVisible(!isOAuth);
        basicUsernameField.setManaged(!isOAuth);
        basicPasswordLabel.setVisible(!isOAuth);
        basicPasswordLabel.setManaged(!isOAuth);
        basicPasswordField.setVisible(!isOAuth);
        basicPasswordField.setManaged(!isOAuth);
    }

    @FXML
    private void onDeepExtractionToggled() {
        boolean enabled = deepExtractionCb.isSelected();
        deepExtractionControls.setVisible(enabled);
        deepExtractionControls.setManaged(enabled);
        if (!enabled) {
            extractValueMappingsCb.setSelected(false);
            extractConfigurationsCb.setSelected(false);
        } else {
            extractValueMappingsCb.setSelected(true);
            extractConfigurationsCb.setSelected(true);
        }
    }

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onDateFilterToggled() {
        boolean enabled = dateFilterEnabledCb.isSelected();
        dateFilterControls.setVisible(enabled);
        dateFilterControls.setManaged(enabled);
        if (!enabled) {
            sinceDatePicker.setValue(null);
        }
    }

    /**
     * Applies the date filter to packages, flows, and value mappings in-place.
     * No-op when the filter is disabled or no date is selected.
     * <p>A package is kept if it passes the filter directly OR if any of its
     * child flows/VMs pass. This avoids losing child artifacts that pre-date
     * the package's own metadata.
     * <p>Must be called from the JavaFX application thread so UI controls are readable.
     */
    // @author Vikas Singh | Created: 2026-02-07
    private void applyDateFilter(ExtractionResult result) {
        // Read UI controls here — this method must be on the FX thread
        boolean enabled = dateFilterEnabledCb.isSelected();
        LocalDate sinceDate = sinceDatePicker.getValue();
        DateFilterUtil.FilterMode mode = dateFilterModeCombo.getValue();
        if (mode == null) mode = DateFilterUtil.FilterMode.MODIFIED_SINCE;
        applyDateFilter(result, enabled, sinceDate, mode);
    }

    // @author Vikas Singh | Created: 2026-02-07
    private void applyDateFilter(ExtractionResult result,
                                 boolean filterEnabled, LocalDate sinceDate,
                                 DateFilterUtil.FilterMode mode) {
        log.info("applyDateFilter called: enabled={}, sinceDate={}, mode={}, runtime={}",
                filterEnabled, sinceDate, mode, result.getRuntimeArtifacts().size());
        if (!filterEnabled) { log.info("Filter disabled — skipping"); return; }
        if (sinceDate == null) { log.info("No sinceDate set — skipping"); return; }
        if (mode == null) mode = DateFilterUtil.FilterMode.MODIFIED_SINCE;

        int origPackages = result.getPackages().size();
        int origFlows = result.getAllFlows().size();
        int origVMs = result.getAllValueMappings().size();
        int origRuntime = result.getRuntimeArtifacts().size();

        // DEPLOYED_SINCE: filter flows by deployedAt and runtime artifacts by deployedOn
        if (mode == DateFilterUtil.FilterMode.DEPLOYED_SINCE) {
            // Log first 3 runtime artifact deployedOn values for diagnostics
            result.getRuntimeArtifacts().stream().limit(3).forEach(rt ->
                log.info("  Runtime sample: id={}, deployedOn={}, parsed={}",
                        rt.getId(), rt.getDeployedOn(),
                        DateFilterUtil.parseCpiDate(rt.getDeployedOn())));

            List<IntegrationPackage> filteredPackages = new ArrayList<>();
            List<IntegrationFlow> filteredAllFlows = new ArrayList<>();

            for (IntegrationPackage pkg : result.getPackages()) {
                List<IntegrationFlow> pkgFlows = new ArrayList<>();
                for (IntegrationFlow flow : pkg.getIntegrationFlows()) {
                    LocalDate dep = DateFilterUtil.parseCpiDate(flow.getDeployedAt());
                    if (dep != null && !dep.isBefore(sinceDate)) {
                        pkgFlows.add(flow);
                        filteredAllFlows.add(flow);
                    }
                }
                pkg.setIntegrationFlows(pkgFlows);
                if (!pkgFlows.isEmpty()) {
                    filteredPackages.add(pkg);
                }
            }

            List<RuntimeArtifact> filteredRuntime = new ArrayList<>();
            for (RuntimeArtifact rt : result.getRuntimeArtifacts()) {
                LocalDate dep = DateFilterUtil.parseCpiDate(rt.getDeployedOn());
                if (dep != null && !dep.isBefore(sinceDate)) {
                    filteredRuntime.add(rt);
                }
            }

            result.setPackages(filteredPackages);
            result.setAllFlows(filteredAllFlows);
            result.setRuntimeArtifacts(filteredRuntime);
            log.info("Date filter (DEPLOYED_SINCE {}): packages {} -> {}, flows {} -> {}, runtime {} -> {}",
                    sinceDate,
                    origPackages, filteredPackages.size(),
                    origFlows, filteredAllFlows.size(),
                    origRuntime, filteredRuntime.size());
            return;
        }

        List<IntegrationPackage> filteredPackages = new ArrayList<>();
        List<IntegrationFlow> filteredAllFlows = new ArrayList<>();
        List<ValueMapping> filteredAllVMs = new ArrayList<>();

        for (IntegrationPackage pkg : result.getPackages()) {
            // Filter flows within this package
            List<IntegrationFlow> pkgFlows = new ArrayList<>();
            for (IntegrationFlow flow : pkg.getIntegrationFlows()) {
                if (DateFilterUtil.passesFilter(flow.getCreatedAt(), flow.getModifiedAt(), sinceDate, mode)) {
                    pkgFlows.add(flow);
                    filteredAllFlows.add(flow);
                }
            }
            pkg.setIntegrationFlows(pkgFlows);

            // Filter value mappings within this package
            List<ValueMapping> pkgVMs = new ArrayList<>();
            for (ValueMapping vm : pkg.getValueMappings()) {
                if (DateFilterUtil.passesFilter(vm.getCreatedAt(), vm.getModifiedAt(), sinceDate, mode)) {
                    pkgVMs.add(vm);
                    filteredAllVMs.add(vm);
                }
            }
            pkg.setValueMappings(pkgVMs);

            // Keep package if it passes OR it still has child flows/VMs
            boolean pkgPasses = DateFilterUtil.passesFilter(
                    pkg.getCreationDate(), pkg.getModifiedDate(), sinceDate, mode);
            if (pkgPasses || !pkgFlows.isEmpty() || !pkgVMs.isEmpty()) {
                filteredPackages.add(pkg);
            }
        }

        // Filter runtime artifacts by deployedOn (the only date available on RuntimeArtifact)
        List<RuntimeArtifact> filteredRuntime = new ArrayList<>();
        for (RuntimeArtifact rt : result.getRuntimeArtifacts()) {
            LocalDate dep = DateFilterUtil.parseCpiDate(rt.getDeployedOn());
            if (dep != null && !dep.isBefore(sinceDate)) {
                filteredRuntime.add(rt);
            }
        }

        result.setPackages(filteredPackages);
        result.setAllFlows(filteredAllFlows);
        result.setAllValueMappings(filteredAllVMs);
        result.setRuntimeArtifacts(filteredRuntime);

        log.info("Date filter ({}, since {}): packages {} -> {}, flows {} -> {}, valueMappings {} -> {}, runtime {} -> {}",
                mode, sinceDate,
                origPackages, filteredPackages.size(),
                origFlows, filteredAllFlows.size(),
                origVMs, filteredAllVMs.size(),
                origRuntime, filteredRuntime.size());
    }

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onLoadConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(msg("fileChooser.loadConfig"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Properties Files", "*.properties"));
        fileChooser.setInitialDirectory(new File("."));

        File file = fileChooser.showOpenDialog(tenantUrlField.getScene().getWindow());
        if (file == null) return;

        try {
            Properties props = new Properties();
            try (InputStream is = new FileInputStream(file)) {
                props.load(is);
            }

            // Populate form fields
            setFieldIfPresent(props, "cpi.base.url", tenantUrlField);
            setFieldIfPresent(props, "cpi.oauth.token.url", oauthTokenUrlField);
            setFieldIfPresent(props, "cpi.oauth.client.id", oauthClientIdField);
            setPasswordIfPresent(props, "cpi.oauth.client.secret", oauthClientSecretField);
            setFieldIfPresent(props, "cpi.basic.username", basicUsernameField);
            setPasswordIfPresent(props, "cpi.basic.password", basicPasswordField);

            String authType = props.getProperty("cpi.auth.type", "oauth2");
            authTypeCombo.setValue("basic".equalsIgnoreCase(authType) ? "Basic" : "OAuth2");
            onAuthTypeChanged();

            // Export settings
            String format = props.getProperty("export.format", "xlsx");
            switch (format.toLowerCase()) {
                case "csv" -> exportFormatCombo.setValue(msg("export.format.csv"));
                case "json" -> exportFormatCombo.setValue(msg("export.format.json"));
                case "all" -> exportFormatCombo.setValue(msg("export.format.all"));
                default -> exportFormatCombo.setValue(msg("export.format.excel"));
            }

            String loadedDir = props.getProperty("export.output.dir");
            if (loadedDir != null && !loadedDir.isBlank() && !"./output".equals(loadedDir.trim())) {
                outputDirField.setText(loadedDir);
            }
            setFieldIfPresent(props, "export.filename.prefix", filenamePrefixField);

            // Extraction options — deep extraction toggle
            boolean deep = getBool(props, "extract.deep", false);
            deepExtractionCb.setSelected(deep);
            onDeepExtractionToggled();
            if (deep) {
                extractValueMappingsCb.setSelected(getBool(props, "extract.valuemappings", true));
                extractConfigurationsCb.setSelected(getBool(props, "extract.configurations", true));
            }

            // Date filter
            dateFilterEnabledCb.setSelected(getBool(props, "filter.date.enabled", false));
            String sinceStr = props.getProperty("filter.date.since", "");
            if (!sinceStr.isBlank()) {
                try { sinceDatePicker.setValue(LocalDate.parse(sinceStr)); } catch (Exception ignored) {}
            }
            String modeStr = props.getProperty("filter.date.mode", "");
            if (!modeStr.isBlank()) {
                try { dateFilterModeCombo.setValue(DateFilterUtil.FilterMode.valueOf(modeStr)); } catch (Exception ignored) {}
            }
            onDateFilterToggled();

            appendLog(msg("log.configLoaded", file.getAbsolutePath()));
        } catch (IOException e) {
            showError(msg("error.loadConfig"), e.getMessage());
        }
    }

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onSaveConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(msg("fileChooser.saveConfig"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Properties Files", "*.properties"));
        fileChooser.setInitialFileName("config.properties");

        File file = fileChooser.showSaveDialog(tenantUrlField.getScene().getWindow());
        if (file == null) return;

        try {
            Properties props = buildPropertiesFromForm();
            try (OutputStream os = new FileOutputStream(file)) {
                props.store(os, "SAP CPI Artifact Extractor Configuration");
            }
            appendLog(msg("log.configSaved", file.getAbsolutePath()));
        } catch (IOException e) {
            showError(msg("error.saveConfig"), e.getMessage());
        }
    }

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(msg("fileChooser.outputDir"));
        try {
            File initial = new File(outputDirField.getText());
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
        } catch (Exception ignored) {}

        File dir = chooser.showDialog(outputDirField.getScene().getWindow());
        if (dir != null) {
            outputDirField.setText(dir.getAbsolutePath());
        }
    }

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onLoadSnapshot() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(msg("fileChooser.loadSnapshot"));
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Snapshot Files", "*.json"));
        fileChooser.setInitialDirectory(new File("."));

        File file = fileChooser.showOpenDialog(tenantUrlField.getScene().getWindow());
        if (file == null) return;

        extractButton.setDisable(true);
        loadSnapshotBtn.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText(msg("progress.loadingSnapshot"));
        logTextArea.clear();

        Task<ExtractionResult> task = new Task<>() {
            @Override
            protected ExtractionResult call() throws Exception {
                SnapshotLoader loader = new SnapshotLoader();
                return loader.load(file);
            }
        };

        task.setOnSucceeded(event -> {
            ExtractionResult result = task.getValue();
            Platform.runLater(() -> {
                progressBar.setProgress(1.0);
                progressLabel.setText(msg("progress.snapshotLoaded"));
                extractButton.setDisable(false);
                loadSnapshotBtn.setDisable(false);
                saveOriginals(result);
                populatePackageCheckboxes(result.getPackages());
                applyDateFilter(result);
                populateResults(result);
                appendLog(msg("log.snapshotLoaded", file.getAbsolutePath()));
            });
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setText(msg("progress.snapshotFailed"));
                extractButton.setDisable(false);
                loadSnapshotBtn.setDisable(false);
                appendLog(msg("log.error", ex.getMessage()));
                showError(msg("error.snapshotLoad"), ex.getMessage());
            });
        });

        Thread thread = new Thread(task, "snapshot-load-thread");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void onFetchPackages() {
        if (tenantUrlField.getText().isBlank()) {
            showError(msg("validation.title"), msg("validation.tenantUrlRequired"));
            return;
        }

        final Properties props = buildPropertiesFromForm();

        fetchPackagesBtn.setDisable(true);
        extractButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText(msg("progress.fetchingPackages"));
        logTextArea.clear();

        Task<List<IntegrationPackage>> task = new Task<>() {
            @Override
            protected List<IntegrationPackage> call() throws Exception {
                Path tempConfig = Files.createTempFile("cpi-ui-config-", ".properties");
                try (OutputStream os = new FileOutputStream(tempConfig.toFile())) {
                    props.store(os, null);
                }

                CpiConfiguration config = new CpiConfiguration(tempConfig.toString());
                config.validate();

                List<IntegrationPackage> packages;
                try (CpiHttpClient httpClient = new CpiHttpClient(config)) {
                    CpiApiService apiService = new CpiApiService(config, httpClient);
                    packages = apiService.getIntegrationPackages();
                }

                Files.deleteIfExists(tempConfig);
                return packages;
            }
        };

        task.setOnSucceeded(event -> {
            List<IntegrationPackage> packages = task.getValue();
            Platform.runLater(() -> {
                fetchedPackages = packages;
                populatePackageCheckboxes(packages);
                packageFilterPane.setExpanded(true);
                progressBar.setProgress(1.0);
                progressLabel.setText(msg("progress.fetchedPackages", packages.size()));
                fetchPackagesBtn.setDisable(false);
                extractButton.setDisable(false);
                appendLog(msg("log.fetchedPackages", packages.size()));
            });
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setText(msg("progress.fetchFailed"));
                fetchPackagesBtn.setDisable(false);
                extractButton.setDisable(false);
                appendLog(msg("log.error", ex.getMessage()));
                showError(msg("error.fetchPackages"), ex.getMessage());
            });
        });

        Thread thread = new Thread(task, "cpi-fetch-packages-thread");
        thread.setDaemon(true);
        thread.start();
    }

    // @author Vikas Singh | Created: 2026-02-07
    @FXML
    private void onExtract() {
        // Validate required fields
        if (tenantUrlField.getText().isBlank()) {
            showError(msg("validation.title"), msg("validation.tenantUrlRequired"));
            return;
        }

        // Capture form state on the FX thread before the background task starts.
        // JavaFX UI controls must only be read from the FX application thread.
        final Properties props = buildPropertiesFromForm();
        final String exportFormat = getExportFormat();
        final boolean autoSnapshot = autoSnapshotCb.isSelected();

        // If packages were pre-fetched, collect checked package IDs
        if (fetchedPackages != null) {
            Set<String> checkedNames = new TreeSet<>();
            for (var node : packageCheckboxContainer.getChildren()) {
                if (node instanceof CheckBox cb && cb.isSelected()) {
                    checkedNames.add(cb.getText());
                }
            }
            // Build comma-separated list of selected package IDs
            List<String> selectedIds = new ArrayList<>();
            for (IntegrationPackage pkg : fetchedPackages) {
                String name = pkg.getName() != null ? pkg.getName() : pkg.getId();
                if (checkedNames.contains(name)) {
                    selectedIds.add(pkg.getId());
                }
            }
            if (!selectedIds.isEmpty() && selectedIds.size() < fetchedPackages.size()) {
                props.setProperty("extract.package.ids", String.join(",", selectedIds));
            }
        }

        extractButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);
        progressLabel.setText(msg("progress.startingExtraction"));
        logTextArea.clear();

        Task<ExtractionResult> task = new Task<>() {
            @Override
            protected ExtractionResult call() throws Exception {
                // Write captured props to temp file for CpiConfiguration to load
                Path tempConfig = Files.createTempFile("cpi-ui-config-", ".properties");
                try (OutputStream os = new FileOutputStream(tempConfig.toFile())) {
                    props.store(os, null);
                }

                CpiConfiguration config = new CpiConfiguration(tempConfig.toString());
                config.validate();

                updateMessage("Connecting to tenant...");

                ExtractionResult result;
                try (CpiHttpClient httpClient = new CpiHttpClient(config)) {
                    CpiApiService apiService = new CpiApiService(config, httpClient);
                    // E8: Pass progress callback that updates Task progress
                    result = apiService.extractAll((phase, progress) -> {
                        updateMessage(phase);
                        updateProgress(progress, 1.0);
                    });
                    result.setApiCallLog(new ArrayList<>(httpClient.getApiCallLog()));
                }

                Files.deleteIfExists(tempConfig);
                return result;
            }
        };

        // E8: Bind progress bar to task progress
        progressBar.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, oldMsg, newMsg) ->
                Platform.runLater(() -> progressLabel.setText(newMsg)));

        task.setOnSucceeded(event -> {
            progressBar.progressProperty().unbind();
            ExtractionResult result = task.getValue();
            Platform.runLater(() -> {
                progressLabel.setText(msg("progress.applyingFilter"));
                // Save originals BEFORE filtering so re-apply works after user changes filter
                saveOriginals(result);
                populatePackageCheckboxes(result.getPackages());
                // Apply filter on FX thread — reads current UI control values safely
                applyDateFilter(result);
                populateResults(result);
                // Export filtered data in a separate background task
                startExport(result, exportFormat, props, autoSnapshot);
            });
        });

        task.setOnFailed(event -> {
            progressBar.progressProperty().unbind();
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setText(msg("progress.extractionFailed"));
                extractButton.setDisable(false);
                appendLog(msg("log.error", ex.getMessage()));
                showError(msg("error.extraction"), ex.getMessage());
            });
        });

        Thread thread = new Thread(task, "cpi-extraction-thread");
        thread.setDaemon(true);
        thread.start();
    }

    // =========================================================================
    // Filter Originals Storage & Re-apply
    // =========================================================================

    // @author Vikas Singh | Created: 2026-02-22
    private void saveOriginals(ExtractionResult result) {
        currentResult = result;
        origPackages = new ArrayList<>(result.getPackages());
        origAllFlows = new ArrayList<>(result.getAllFlows());
        origAllVMs = new ArrayList<>(result.getAllValueMappings());
        origRuntimeArtifacts = new ArrayList<>(result.getRuntimeArtifacts());
        origPkgFlows.clear();
        origPkgVMs.clear();
        for (IntegrationPackage pkg : origPackages) {
            origPkgFlows.put(pkg.getId(), new ArrayList<>(pkg.getIntegrationFlows()));
            origPkgVMs.put(pkg.getId(), new ArrayList<>(pkg.getValueMappings()));
        }
    }

    private void populatePackageCheckboxes(List<IntegrationPackage> packages) {
        updatingPackageCheckboxes = true;
        packageCheckboxContainer.getChildren().clear();
        Set<String> names = new TreeSet<>();
        for (IntegrationPackage pkg : packages) {
            String name = pkg.getName() != null ? pkg.getName() : pkg.getId();
            names.add(name);
        }
        for (String name : names) {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(true);
            cb.selectedProperty().addListener((obs, old, val) -> {
                if (!updatingPackageCheckboxes) reapplyFilter();
            });
            packageCheckboxContainer.getChildren().add(cb);
        }
        selectAllPackagesCb.setSelected(true);
        updatingPackageCheckboxes = false;
    }

    // @author Vikas Singh | Created: 2026-02-22
    private void reapplyFilter() {
        if (currentResult == null) return;
        // Restore all original unfiltered data into currentResult before re-filtering
        currentResult.setPackages(new ArrayList<>(origPackages));
        currentResult.setAllFlows(new ArrayList<>(origAllFlows));
        currentResult.setAllValueMappings(new ArrayList<>(origAllVMs));
        currentResult.setRuntimeArtifacts(new ArrayList<>(origRuntimeArtifacts));
        for (IntegrationPackage pkg : currentResult.getPackages()) {
            List<IntegrationFlow> flows = origPkgFlows.get(pkg.getId());
            pkg.setIntegrationFlows(flows != null ? new ArrayList<>(flows) : new ArrayList<>());
            List<ValueMapping> vms = origPkgVMs.get(pkg.getId());
            pkg.setValueMappings(vms != null ? new ArrayList<>(vms) : new ArrayList<>());
        }
        applyDateFilter(currentResult);
        applyPackageFilter(currentResult);
        populateResults(currentResult);
    }

    private void applyPackageFilter(ExtractionResult result) {
        // Collect checked package names
        Set<String> checkedNames = new TreeSet<>();
        boolean allChecked = true;
        for (var node : packageCheckboxContainer.getChildren()) {
            if (node instanceof CheckBox cb) {
                if (cb.isSelected()) {
                    checkedNames.add(cb.getText());
                } else {
                    allChecked = false;
                }
            }
        }
        // If all checked or no checkboxes exist, show everything
        if (allChecked || checkedNames.isEmpty()) return;

        List<IntegrationPackage> filteredPackages = new ArrayList<>();
        List<IntegrationFlow> filteredFlows = new ArrayList<>();
        List<ValueMapping> filteredVMs = new ArrayList<>();

        for (IntegrationPackage pkg : result.getPackages()) {
            String pkgName = pkg.getName() != null ? pkg.getName() : pkg.getId();
            if (checkedNames.contains(pkgName)) {
                filteredPackages.add(pkg);
                filteredFlows.addAll(pkg.getIntegrationFlows());
                filteredVMs.addAll(pkg.getValueMappings());
            }
        }

        result.setPackages(filteredPackages);
        result.setAllFlows(filteredFlows);
        result.setAllValueMappings(filteredVMs);
    }

    // @author Vikas Singh | Created: 2026-02-22
    private void startExport(ExtractionResult result, String exportFormat,
                             Properties props, boolean autoSnapshot) {
        String outputDir = props.getProperty("export.output.dir", "C:\\temp\\CPI Extracts");
        String prefix = props.getProperty("export.filename.prefix", "cpi_artifacts");
        Task<Void> exportTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                switch (exportFormat) {
                    case "xlsx" -> new ExcelExporter().export(result, outputDir, prefix);
                    case "csv" -> new CsvExporter().export(result, outputDir, prefix);
                    case "json" -> new JsonExporter().export(result, outputDir, prefix);
                    case "all" -> {
                        new ExcelExporter().export(result, outputDir, prefix);
                        new CsvExporter().export(result, outputDir, prefix);
                        new JsonExporter().export(result, outputDir, prefix);
                    }
                }
                // E17: Auto-save snapshot
                if (autoSnapshot) {
                    String snapshotPath = new JsonExporter().export(result, outputDir, prefix + "_snapshot");
                    Platform.runLater(() -> appendLog(msg("log.autoSnapshot", snapshotPath)));
                }
                return null;
            }
        };
        exportTask.setOnSucceeded(e -> Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            progressLabel.setText(msg("progress.extractionComplete"));
            extractButton.setDisable(false);
            appendLog(msg("log.extractionComplete"));
        }));
        exportTask.setOnFailed(e -> {
            Throwable ex = exportTask.getException();
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setText(msg("progress.exportFailed"));
                extractButton.setDisable(false);
                appendLog(msg("log.error", ex.getMessage()));
                showError(msg("error.export"), ex.getMessage());
            });
        });
        Thread exportThread = new Thread(exportTask, "cpi-export-thread");
        exportThread.setDaemon(true);
        exportThread.start();
    }

    // =========================================================================
    // Results Population
    // =========================================================================

    // @author Vikas Singh | Created: 2026-02-07
    private void populateResults(ExtractionResult result) {
        // Summary tab
        String summary = result.getSummary();
        if (dateFilterEnabledCb.isSelected() && sinceDatePicker.getValue() != null) {
            summary += String.format(
                    msg("dateFilter.summary"),
                    dateFilterModeCombo.getValue(), sinceDatePicker.getValue());
        }
        summaryTextArea.setText(summary);

        // Packages tab
        packagesTable.setItems(FXCollections.observableArrayList(result.getPackages()));

        // Flows tab
        flowsTable.setItems(FXCollections.observableArrayList(result.getAllFlows()));

        // Value Mappings tab
        valueMapsTable.setItems(FXCollections.observableArrayList(result.getAllValueMappings()));

        // Configurations tab - flatten into rows
        List<ConfigRow> configRows = new ArrayList<>();
        for (IntegrationFlow flow : result.getAllFlows()) {
            for (Configuration cfg : flow.getConfigurations()) {
                configRows.add(new ConfigRow(flow.getId(), flow.getName(),
                        cfg.getParameterKey(), cfg.getParameterValue(), cfg.getDataType()));
            }
        }
        configsTable.setItems(FXCollections.observableArrayList(configRows));

        // Runtime tab
        runtimeTable.setItems(FXCollections.observableArrayList(result.getRuntimeArtifacts()));

        // iFlow Adapters tab — flattened from parsed bundles
        List<AdapterRow> adapterRows = new ArrayList<>();
        for (IntegrationFlow flow : result.getAllFlows()) {
            if (flow.isBundleParsed() && flow.getIflowContent() != null) {
                for (var adapter : flow.getIflowContent().getAdapters()) {
                    adapterRows.add(new AdapterRow(
                            flow.getId(), flow.getName(),
                            adapter.getAdapterType(), adapter.getDirection(),
                            adapter.getAddress(), adapter.getTransportProtocol()));
                }
            }
        }
        log.info("Adapters tab: {} rows from {} flows ({} bundle-parsed)",
                adapterRows.size(), result.getAllFlows().size(),
                result.getAllFlows().stream().filter(IntegrationFlow::isBundleParsed).count());
        // Debug: log JMS/ProcessDirect adapter properties to help diagnose chain detection
        for (IntegrationFlow flow : result.getAllFlows()) {
            if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
            for (var adapter : flow.getIflowContent().getAdapters()) {
                String type = adapter.getAdapterType() != null ? adapter.getAdapterType().toLowerCase() : "";
                if (type.contains("jms") || type.contains("processdirect")) {
                    log.info("Chain adapter: flow={}, type={}, dir={}, address={}, props={}",
                            flow.getId(), adapter.getAdapterType(), adapter.getDirection(),
                            adapter.getAddress(), adapter.getProperties());
                }
            }
        }
        adaptersTable.setItems(FXCollections.observableArrayList(adapterRows));

        // Build flow-to-package mapping (shared across usage, ECC, chains, credentials tabs)
        Map<String, String> flowToPackage = new LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow flow : pkg.getIntegrationFlows()) {
                String flowName = flow.getName() != null ? flow.getName() : flow.getId();
                if (flowName != null) {
                    flowToPackage.put(flowName, pkg.getName() != null ? pkg.getName() : pkg.getId());
                }
            }
        }

        // iFlow Usage tab — show all flows with MPL aggregation; mark unused flows (E2)
        List<IFlowUsageRow> usageRows = new ArrayList<>();
        {
            // Build MPL lookup by flow name
            Map<String, List<MessageProcessingLog>> mplByFlow = new LinkedHashMap<>();
            if (result.getMessageProcessingLogs() != null) {
                for (MessageProcessingLog mpl : result.getMessageProcessingLogs()) {
                    String name = mpl.getIntegrationFlowName();
                    if (name != null && !name.isBlank()) {
                        mplByFlow.computeIfAbsent(name, k -> new ArrayList<>()).add(mpl);
                    }
                }
            }

            // Iterate all extracted flows
            for (IntegrationFlow flow : result.getAllFlows()) {
                String flowName = flow.getName() != null ? flow.getName() : flow.getId();
                String flowId = flow.getId() != null ? flow.getId() : flowName;
                if (flowName == null) continue;
                String pkgName = flowToPackage.getOrDefault(flowName, "");

                // Try matching by Id first, then by Name
                List<MessageProcessingLog> logs = mplByFlow.get(flowId);
                if ((logs == null || logs.isEmpty()) && !flowId.equals(flowName)) {
                    logs = mplByFlow.get(flowName);
                }

                // E2: Compute deployed status
                String runtimeStatus = flow.getRuntimeStatus() != null ? flow.getRuntimeStatus() : "UNKNOWN";
                String deployedStatus;
                boolean noLogs = (logs == null || logs.isEmpty());
                if ("STARTED".equalsIgnoreCase(runtimeStatus) && noLogs) {
                    deployedStatus = "Unused Deployed";
                } else if ("STARTED".equalsIgnoreCase(runtimeStatus)) {
                    deployedStatus = "Active";
                } else if ("NOT_DEPLOYED".equalsIgnoreCase(runtimeStatus)) {
                    deployedStatus = "Not Deployed";
                } else if ("ERROR".equalsIgnoreCase(runtimeStatus)) {
                    deployedStatus = "Error";
                } else {
                    deployedStatus = runtimeStatus;
                }

                if (noLogs) {
                    usageRows.add(new IFlowUsageRow(pkgName, flowName, 0, 0, 0, 0, 0, "",
                            "Not Used", runtimeStatus, deployedStatus));
                } else {
                    int total = logs.size();
                    int completed = 0, failed = 0, retry = 0, escalated = 0;
                    String lastExec = "";
                    String lastStatus = "";
                    for (MessageProcessingLog m : logs) {
                        String s = m.getStatus() != null ? m.getStatus().toUpperCase() : "";
                        switch (s) {
                            case "COMPLETED" -> completed++;
                            case "FAILED" -> failed++;
                            case "RETRY" -> retry++;
                            case "ESCALATED" -> escalated++;
                        }
                        String logEnd = m.getLogEnd() != null ? m.getLogEnd() : "";
                        if (logEnd.compareTo(lastExec) > 0) {
                            lastExec = logEnd;
                            lastStatus = m.getStatus() != null ? m.getStatus() : "";
                        }
                    }
                    usageRows.add(new IFlowUsageRow(pkgName, flowName, total, completed, failed,
                            retry, escalated, DateFilterUtil.formatCpiDate(lastExec), lastStatus,
                            runtimeStatus, deployedStatus));
                }
            }
        }
        iflowUsageTable.setItems(FXCollections.observableArrayList(usageRows));

        // Credentials tab — extract security material references from adapter properties (E4)
        List<CredentialRow> credentialRows = new ArrayList<>();
        for (IntegrationFlow flow : result.getAllFlows()) {
            if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
            String flowName = flow.getName() != null ? flow.getName() : flow.getId();
            String pkgName = flowToPackage.getOrDefault(flowName, "");

            for (var adapter : flow.getIflowContent().getAdapters()) {
                String adapterType = adapter.getAdapterType() != null ? adapter.getAdapterType() : "";
                String direction = adapter.getDirection() != null ? adapter.getDirection() : "";

                for (var entry : adapter.getProperties().entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    if (value == null || value.isBlank()) continue;
                    if (isCredentialProperty(key)) {
                        credentialRows.add(new CredentialRow(pkgName, flowName, adapterType,
                                direction, value, classifyCredentialType(key), key,
                                "Adapter: " + adapterType));
                    }
                }
            }

            // Also scan process properties for credential references
            for (var entry : flow.getIflowContent().getProcessProperties().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null || value.isBlank()) continue;
                if (isCredentialProperty(key)) {
                    credentialRows.add(new CredentialRow(
                            pkgName, flowName, "", "", value,
                            classifyCredentialType(key), key, "Process Property"));
                }
            }
        }
        credentialsTable.setItems(FXCollections.observableArrayList(credentialRows));

        // ECC Endpoints tab — flag adapters using ECC-specific protocols
        List<EccEndpointRow> eccRows = new ArrayList<>();
        for (IntegrationFlow flow : result.getAllFlows()) {
            if (flow.isBundleParsed() && flow.getIflowContent() != null) {
                String pkgName = flowToPackage.getOrDefault(
                        flow.getName() != null ? flow.getName() : flow.getId(), "");
                for (var adapter : flow.getIflowContent().getAdapters()) {
                    String type = adapter.getAdapterType() != null ? adapter.getAdapterType() : "";
                    String protocol = adapter.getTransportProtocol() != null ? adapter.getTransportProtocol() : "";
                    String msgProtocol = adapter.getMessageProtocol() != null ? adapter.getMessageProtocol() : "";
                    String category = classifyEndpoint(type, protocol, msgProtocol);
                    String address = adapter.getAddress() != null ? adapter.getAddress() : "";
                    eccRows.add(new EccEndpointRow(pkgName,
                            flow.getName() != null ? flow.getName() : flow.getId(),
                            adapter.getDirection(), type, protocol, msgProtocol,
                            address, category));
                }
            }
        }
        eccEndpointsTable.setItems(FXCollections.observableArrayList(eccRows));

        // Flow Chains tab — detect JMS and ProcessDirect linked flows
        List<FlowChainRow> chainRows = buildFlowChains(result);
        flowChainsTable.setItems(FXCollections.observableArrayList(chainRows));

        // API Calls tab
        List<ApiCallRow> apiCallRows = new ArrayList<>();
        for (CpiHttpClient.ApiCallRecord rec : result.getApiCallLog()) {
            apiCallRows.add(new ApiCallRow(rec.method(), rec.path(), rec.statusCode(),
                    rec.durationMs() + " ms"));
        }
        apiCallsTable.setItems(FXCollections.observableArrayList(apiCallRows));

        // Switch to Summary tab
        resultsTabPane.getSelectionModel().selectFirst();
    }

    // =========================================================================
    // Table Initialization
    // =========================================================================

    // @author Vikas Singh | Created: 2026-02-07
    private void initPackagesTable() {
        addColumn(packagesTable, msg("col.packageId"), "id");
        addColumn(packagesTable, msg("col.name"), "name");
        addColumn(packagesTable, msg("col.description"), "description");
        addColumn(packagesTable, msg("col.version"), "version");
        addColumn(packagesTable, msg("col.vendor"), "vendor");
        addColumn(packagesTable, msg("col.mode"), "mode");
        addColumn(packagesTable, msg("col.createdBy"), "createdBy");
        addFormattedDateColumn(packagesTable, msg("col.creationDate"), IntegrationPackage::getCreationDate);
        addColumn(packagesTable, msg("col.modifiedBy"), "modifiedBy");
        addFormattedDateColumn(packagesTable, msg("col.modifiedDate"), IntegrationPackage::getModifiedDate);
        packagesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // @author Vikas Singh | Created: 2026-02-07
    private void initFlowsTable() {
        addColumn(flowsTable, msg("col.flowId"), "id");
        addColumn(flowsTable, msg("col.name"), "name");
        addColumn(flowsTable, msg("col.packageIdRef"), "packageId");
        addColumn(flowsTable, msg("col.version"), "version");
        addColumn(flowsTable, msg("col.sender"), "sender");
        addColumn(flowsTable, msg("col.receiver"), "receiver");
        addColumn(flowsTable, msg("col.runtimeStatus"), "runtimeStatus");
        addColumn(flowsTable, msg("col.deployedVersion"), "deployedVersion");
        addColumn(flowsTable, msg("col.createdBy"), "createdBy");
        addFormattedDateColumn(flowsTable, msg("col.createdAt"), IntegrationFlow::getCreatedAt);
        addColumn(flowsTable, msg("col.modifiedBy"), "modifiedBy");
        addFormattedDateColumn(flowsTable, msg("col.modifiedAt"), IntegrationFlow::getModifiedAt);
        flowsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // @author Vikas Singh | Created: 2026-02-08
    private void initValueMapsTable() {
        addColumn(valueMapsTable, msg("col.id"), "id");
        addColumn(valueMapsTable, msg("col.name"), "name");
        addColumn(valueMapsTable, msg("col.description"), "description");
        addColumn(valueMapsTable, msg("col.packageIdRef"), "packageId");
        addColumn(valueMapsTable, msg("col.version"), "version");
        addColumn(valueMapsTable, msg("col.createdBy"), "createdBy");
        addFormattedDateColumn(valueMapsTable, msg("col.createdAt"), ValueMapping::getCreatedAt);
        addColumn(valueMapsTable, msg("col.modifiedBy"), "modifiedBy");
        addFormattedDateColumn(valueMapsTable, msg("col.modifiedAt"), ValueMapping::getModifiedAt);
        addColumn(valueMapsTable, msg("col.runtimeStatus"), "runtimeStatus");
        valueMapsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // @author Vikas Singh | Created: 2026-02-08
    @SuppressWarnings("unchecked")
    private void initConfigsTable() {
        TableColumn<ConfigRow, String> artifactIdCol = new TableColumn<>(msg("col.artifactId"));
        artifactIdCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().artifactId()));

        TableColumn<ConfigRow, String> artifactNameCol = new TableColumn<>(msg("col.artifactName"));
        artifactNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().artifactName()));

        TableColumn<ConfigRow, String> keyCol = new TableColumn<>(msg("col.parameterKey"));
        keyCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().parameterKey()));

        TableColumn<ConfigRow, String> valueCol = new TableColumn<>(msg("col.parameterValue"));
        valueCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().parameterValue()));

        TableColumn<ConfigRow, String> typeCol = new TableColumn<>(msg("col.dataType"));
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().dataType()));

        configsTable.getColumns().addAll(artifactIdCol, artifactNameCol, keyCol, valueCol, typeCol);
        configsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // @author Vikas Singh | Created: 2026-02-08
    private void initRuntimeTable() {
        addColumn(runtimeTable, msg("col.artifactId"), "id");
        addColumn(runtimeTable, msg("col.name"), "name");
        addColumn(runtimeTable, msg("col.type"), "type");
        addColumn(runtimeTable, msg("col.version"), "version");
        addColumn(runtimeTable, msg("col.status"), "status");
        addColumn(runtimeTable, msg("col.deployedBy"), "deployedBy");
        addFormattedDateColumn(runtimeTable, msg("col.deployedOn"), RuntimeArtifact::getDeployedOn);
        addColumn(runtimeTable, msg("col.errorInfo"), "errorInformation");
        runtimeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // @author Vikas Singh | Created: 2026-02-22
    @SuppressWarnings("unchecked")
    private void initAdaptersTable() {
        TableColumn<AdapterRow, String> flowIdCol = new TableColumn<>(msg("col.flowId"));
        flowIdCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().flowId()));

        TableColumn<AdapterRow, String> flowNameCol = new TableColumn<>(msg("col.flowName"));
        flowNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().flowName()));

        TableColumn<AdapterRow, String> typeCol = new TableColumn<>(msg("col.adapterType"));
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().adapterType()));

        TableColumn<AdapterRow, String> dirCol = new TableColumn<>(msg("col.direction"));
        dirCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().direction()));

        TableColumn<AdapterRow, String> addressCol = new TableColumn<>(msg("col.address"));
        addressCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().address()));

        TableColumn<AdapterRow, String> protocolCol = new TableColumn<>(msg("col.transportProtocol"));
        protocolCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().transportProtocol()));

        adaptersTable.getColumns().addAll(flowIdCol, flowNameCol, typeCol, dirCol, addressCol, protocolCol);
        adaptersTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    @SuppressWarnings("unchecked")
    private void initIflowUsageTable() {
        TableColumn<IFlowUsageRow, String> pkgCol = new TableColumn<>(msg("col.package"));
        pkgCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().packageName()));
        pkgCol.setPrefWidth(200);

        TableColumn<IFlowUsageRow, String> nameCol = new TableColumn<>(msg("col.iflowName"));
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().flowName()));
        nameCol.setPrefWidth(250);

        TableColumn<IFlowUsageRow, String> totalCol = new TableColumn<>(msg("col.total"));
        totalCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().total())));

        TableColumn<IFlowUsageRow, String> completedCol = new TableColumn<>(msg("col.completed"));
        completedCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().completed())));

        TableColumn<IFlowUsageRow, String> failedCol = new TableColumn<>(msg("col.failed"));
        failedCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().failed())));

        TableColumn<IFlowUsageRow, String> retryCol = new TableColumn<>(msg("col.retry"));
        retryCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().retry())));

        TableColumn<IFlowUsageRow, String> escalatedCol = new TableColumn<>(msg("col.escalated"));
        escalatedCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().escalated())));

        TableColumn<IFlowUsageRow, String> lastExecCol = new TableColumn<>(msg("col.lastExecution"));
        lastExecCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().lastExecution()));
        lastExecCol.setPrefWidth(180);

        TableColumn<IFlowUsageRow, String> lastStatusCol = new TableColumn<>(msg("col.lastStatus"));
        lastStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().lastStatus()));

        // E2: Runtime Status and Deployed Status columns
        TableColumn<IFlowUsageRow, String> runtimeStatusCol = new TableColumn<>(msg("col.runtimeStatus"));
        runtimeStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().runtimeStatus()));

        TableColumn<IFlowUsageRow, String> deployedStatusCol = new TableColumn<>(msg("col.deployedStatus"));
        deployedStatusCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().deployedStatus()));
        deployedStatusCol.setPrefWidth(140);

        iflowUsageTable.getColumns().addAll(pkgCol, nameCol, totalCol, completedCol, failedCol,
                retryCol, escalatedCol, lastExecCol, lastStatusCol, runtimeStatusCol, deployedStatusCol);
        iflowUsageTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // E4: Credentials table
    @SuppressWarnings("unchecked")
    private void initCredentialsTable() {
        TableColumn<CredentialRow, String> pkgCol = new TableColumn<>(msg("col.package"));
        pkgCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().packageName()));

        TableColumn<CredentialRow, String> flowCol = new TableColumn<>(msg("col.iflow"));
        flowCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().flowName()));
        flowCol.setPrefWidth(200);

        TableColumn<CredentialRow, String> adapterCol = new TableColumn<>(msg("col.adapterType"));
        adapterCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().adapterType()));

        TableColumn<CredentialRow, String> dirCol = new TableColumn<>(msg("col.direction"));
        dirCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().direction()));

        TableColumn<CredentialRow, String> credNameCol = new TableColumn<>(msg("col.credentialName"));
        credNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().credentialName()));
        credNameCol.setPrefWidth(200);

        TableColumn<CredentialRow, String> credTypeCol = new TableColumn<>(msg("col.credentialType"));
        credTypeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().credentialType()));
        credTypeCol.setPrefWidth(140);

        TableColumn<CredentialRow, String> propKeyCol = new TableColumn<>(msg("col.propertyKey"));
        propKeyCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().propertyKey()));
        propKeyCol.setPrefWidth(180);

        TableColumn<CredentialRow, String> contextCol = new TableColumn<>(msg("col.context"));
        contextCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().context()));

        credentialsTable.getColumns().addAll(pkgCol, flowCol, adapterCol, dirCol,
                credNameCol, credTypeCol, propKeyCol, contextCol);
        credentialsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    @SuppressWarnings("unchecked")
    private void initEccEndpointsTable() {
        TableColumn<EccEndpointRow, String> pkgCol = new TableColumn<>(msg("col.package"));
        pkgCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().packageName()));

        TableColumn<EccEndpointRow, String> flowCol = new TableColumn<>(msg("col.iflow"));
        flowCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().flowName()));
        flowCol.setPrefWidth(200);

        TableColumn<EccEndpointRow, String> dirCol = new TableColumn<>(msg("col.direction"));
        dirCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().direction()));

        TableColumn<EccEndpointRow, String> typeCol = new TableColumn<>(msg("col.adapterType"));
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().adapterType()));

        TableColumn<EccEndpointRow, String> protoCol = new TableColumn<>(msg("col.transport"));
        protoCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().transportProtocol()));

        TableColumn<EccEndpointRow, String> msgProtoCol = new TableColumn<>(msg("col.messageProtocol"));
        msgProtoCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().messageProtocol()));

        TableColumn<EccEndpointRow, String> addrCol = new TableColumn<>(msg("col.address"));
        addrCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().address()));
        addrCol.setPrefWidth(250);

        TableColumn<EccEndpointRow, String> catCol = new TableColumn<>(msg("col.category"));
        catCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().category()));
        catCol.setPrefWidth(180);

        eccEndpointsTable.getColumns().addAll(pkgCol, flowCol, dirCol, typeCol, protoCol, msgProtoCol, addrCol, catCol);
        eccEndpointsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    @SuppressWarnings("unchecked")
    private void initFlowChainsTable() {
        TableColumn<FlowChainRow, String> typeCol = new TableColumn<>(msg("col.chainType"));
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().chainType()));

        TableColumn<FlowChainRow, String> queueCol = new TableColumn<>(msg("col.queueAddress"));
        queueCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().queueOrAddress()));
        queueCol.setPrefWidth(250);

        TableColumn<FlowChainRow, String> senderFlowCol = new TableColumn<>(msg("col.senderIflow"));
        senderFlowCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().senderFlow()));
        senderFlowCol.setPrefWidth(200);

        TableColumn<FlowChainRow, String> senderPkgCol = new TableColumn<>(msg("col.senderPackage"));
        senderPkgCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().senderPackage()));

        TableColumn<FlowChainRow, String> recFlowCol = new TableColumn<>(msg("col.receiverIflow"));
        recFlowCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().receiverFlow()));
        recFlowCol.setPrefWidth(200);

        TableColumn<FlowChainRow, String> recPkgCol = new TableColumn<>(msg("col.receiverPackage"));
        recPkgCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().receiverPackage()));

        flowChainsTable.getColumns().addAll(typeCol, queueCol, senderFlowCol, senderPkgCol, recFlowCol, recPkgCol);
        flowChainsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    @SuppressWarnings("unchecked")
    private void initApiCallsTable() {
        TableColumn<ApiCallRow, String> methodCol = new TableColumn<>(msg("col.method"));
        methodCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().method()));

        TableColumn<ApiCallRow, String> pathCol = new TableColumn<>(msg("col.path"));
        pathCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().path()));
        pathCol.setPrefWidth(400);

        TableColumn<ApiCallRow, String> statusCol = new TableColumn<>(msg("col.statusCode"));
        statusCol.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().statusCode())));

        TableColumn<ApiCallRow, String> durationCol = new TableColumn<>(msg("col.duration"));
        durationCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().duration()));

        apiCallsTable.getColumns().addAll(methodCol, pathCol, statusCol, durationCol);
        apiCallsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    // @author Vikas Singh | Created: 2026-02-08
    private <T> void addColumn(TableView<T> table, String title, String property) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        table.getColumns().add(col);
    }

    // @author Vikas Singh | Created: 2026-02-08
    private <T> void addFormattedDateColumn(TableView<T> table, String title,
                                             Function<T, String> dateGetter) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(cellData ->
                new SimpleStringProperty(DateFilterUtil.formatCpiDate(dateGetter.apply(cellData.getValue()))));
        table.getColumns().add(col);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    // @author Vikas Singh | Created: 2026-02-08
    private Properties buildPropertiesFromForm() {
        Properties props = new Properties();

        props.setProperty("cpi.base.url", tenantUrlField.getText().trim());

        boolean isOAuth = "OAuth2".equals(authTypeCombo.getValue());
        props.setProperty("cpi.auth.type", isOAuth ? "oauth2" : "basic");

        if (isOAuth) {
            props.setProperty("cpi.oauth.token.url", oauthTokenUrlField.getText().trim());
            props.setProperty("cpi.oauth.client.id", oauthClientIdField.getText().trim());
            props.setProperty("cpi.oauth.client.secret", oauthClientSecretField.getText().trim());
        } else {
            props.setProperty("cpi.basic.username", basicUsernameField.getText().trim());
            props.setProperty("cpi.basic.password", basicPasswordField.getText().trim());
        }

        props.setProperty("export.format", getExportFormat());
        props.setProperty("export.output.dir", outputDirField.getText().trim());
        props.setProperty("export.filename.prefix", filenamePrefixField.getText().trim());

        // Core options (always on)
        props.setProperty("extract.packages", "true");
        props.setProperty("extract.flows", "true");
        props.setProperty("extract.runtime.status", "true");
        props.setProperty("extract.message.logs", "true");

        // Deep extraction options
        props.setProperty("extract.deep", String.valueOf(deepExtractionCb.isSelected()));
        props.setProperty("extract.valuemappings", String.valueOf(extractValueMappingsCb.isSelected()));
        props.setProperty("extract.configurations", String.valueOf(extractConfigurationsCb.isSelected()));

        props.setProperty("filter.date.enabled", String.valueOf(dateFilterEnabledCb.isSelected()));
        LocalDate sinceDate = sinceDatePicker.getValue();
        props.setProperty("filter.date.since", sinceDate != null ? sinceDate.toString() : "");
        DateFilterUtil.FilterMode filterMode = dateFilterModeCombo.getValue();
        props.setProperty("filter.date.mode",
                filterMode != null ? filterMode.name() : DateFilterUtil.FilterMode.MODIFIED_SINCE.name());

        return props;
    }

    // @author Vikas Singh | Created: 2026-02-08
    private String getExportFormat() {
        String selected = exportFormatCombo.getValue();
        if (selected == null) return "xlsx";
        if (selected.equals(msg("export.format.csv"))) return "csv";
        if (selected.equals(msg("export.format.json"))) return "json";
        if (selected.equals(msg("export.format.all"))) return "all";
        return "xlsx";
    }

    // @author Vikas Singh | Created: 2026-02-08
    private void setFieldIfPresent(Properties props, String key, TextField field) {
        String val = props.getProperty(key);
        if (val != null && !val.isBlank()) {
            field.setText(val);
        }
    }

    // @author Vikas Singh | Created: 2026-02-08
    private void setPasswordIfPresent(Properties props, String key, PasswordField field) {
        String val = props.getProperty(key);
        if (val != null && !val.isBlank()) {
            field.setText(val);
        }
    }

    // @author Vikas Singh | Created: 2026-02-08
    private boolean getBool(Properties props, String key, boolean defaultVal) {
        String val = props.getProperty(key);
        return val != null ? Boolean.parseBoolean(val) : defaultVal;
    }

    // @author Vikas Singh | Created: 2026-02-08
    private void appendLog(String message) {
        Platform.runLater(() -> {
            logTextArea.appendText(message + "\n");
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    // @author Vikas Singh | Created: 2026-02-08
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // =========================================================================
    // Row Records
    // =========================================================================

    public record ConfigRow(String artifactId, String artifactName,
                            String parameterKey, String parameterValue, String dataType) {}

    public record AdapterRow(String flowId, String flowName,
                             String adapterType, String direction,
                             String address, String transportProtocol) {}

    public record ApiCallRow(String method, String path, int statusCode, String duration) {}

    // E2: Added runtimeStatus and deployedStatus fields
    public record IFlowUsageRow(String packageName, String flowName, int total, int completed, int failed,
                                int retry, int escalated, String lastExecution, String lastStatus,
                                String runtimeStatus, String deployedStatus) {}

    public record EccEndpointRow(String packageName, String flowName, String direction,
                                  String adapterType, String transportProtocol, String messageProtocol,
                                  String address, String category) {}

    public record FlowChainRow(String chainType, String queueOrAddress,
                                String senderFlow, String senderPackage,
                                String receiverFlow, String receiverPackage) {}

    // E4: Credential inventory row
    public record CredentialRow(String packageName, String flowName, String adapterType,
                                 String direction, String credentialName, String credentialType,
                                 String propertyKey, String context) {}

    // =========================================================================
    // E4: Credential Detection Helpers
    // =========================================================================

    private static final Set<String> CREDENTIAL_KEYS = Set.of(
            "credential_name", "credentialname", "credential.name",
            "private.key.alias", "privatekeyalias",
            "public.key.alias", "publickeyalias",
            "certificate.alias", "certificatealias",
            "senderauthcredential", "sender.auth.credential",
            "receiverauthcredential", "receiver.auth.credential",
            "proxyuser", "proxy.user",
            "securitymaterial", "security.material",
            "pgp.secret.keyring.alias", "pgpsecretkeyringalias",
            "pgp.public.keyring.alias", "pgppublickeyringalias"
    );

    private static boolean isCredentialProperty(String key) {
        String lower = key.toLowerCase();
        if (CREDENTIAL_KEYS.contains(lower)) return true;
        return lower.contains("credential") || lower.contains("keystore")
                || lower.contains("certificate") || lower.contains("alias")
                || lower.contains("secret.key") || lower.contains("pgp");
    }

    private static String classifyCredentialType(String propertyKey) {
        String lower = propertyKey.toLowerCase();
        if (lower.contains("oauth")) return "OAuth2";
        if (lower.contains("saml")) return "SAML";
        if (lower.contains("pgp")) return "PGP";
        if (lower.contains("keystore") || lower.contains("key.alias")
                || lower.contains("keyalias") || lower.contains("certificate")) return "Keystore";
        if (lower.contains("credential")) return "Credential";
        return "Security Material";
    }

    // =========================================================================
    // ECC Endpoint Classification
    // =========================================================================

    private static String classifyEndpoint(String adapterType, String transportProtocol, String messageProtocol) {
        String type = adapterType.toLowerCase();
        String proto = transportProtocol.toLowerCase();
        String msgProto = messageProtocol.toLowerCase();

        // ECC-specific
        if (type.contains("idoc") || msgProto.contains("idoc")) return "ECC (IDoc)";
        if (type.contains("rfc") || proto.contains("rfc")) return "ECC (RFC/BAPI)";
        if (type.contains("xi") || type.contains("soap") && msgProto.contains("xi")) return "ECC (XI/SOAP)";
        if (type.contains("as2")) return "Legacy (AS2)";

        // S/4-compatible
        if (type.contains("odata") || proto.contains("odata")) return "S/4 Compatible (OData)";
        if (type.contains("http") || type.contains("rest")) return "S/4 Compatible (HTTP/REST)";
        if (type.contains("soap")) return "Neutral (SOAP)";

        // Middleware
        if (type.contains("jms")) return "Middleware (JMS)";
        if (type.contains("processdirect")) return "Internal (ProcessDirect)";
        if (type.contains("sftp") || type.contains("ftp")) return "Neutral (SFTP/FTP)";
        if (type.contains("mail") || type.contains("smtp") || type.contains("imap")) return "Neutral (Mail)";
        if (type.contains("kafka")) return "Neutral (Kafka)";
        if (type.contains("amqp")) return "Neutral (AMQP)";
        if (type.contains("ariba")) return "Neutral (Ariba)";
        if (type.contains("successfactors") || type.contains("sfsf")) return "Neutral (SuccessFactors)";
        if (type.contains("elster")) return "Legacy (ELSTER)";

        return "Other (" + adapterType + ")";
    }

    // =========================================================================
    // Flow Chain Detection (JMS / ProcessDirect)
    // =========================================================================

    private List<FlowChainRow> buildFlowChains(ExtractionResult result) {
        // Build flow-to-package mapping
        Map<String, String> flowToPackage = new LinkedHashMap<>();
        for (IntegrationPackage pkg : result.getPackages()) {
            for (IntegrationFlow f : pkg.getIntegrationFlows()) {
                String flowName = f.getName() != null ? f.getName() : f.getId();
                flowToPackage.put(f.getId(), pkg.getName() != null ? pkg.getName() : pkg.getId());
                if (f.getName() != null) flowToPackage.put(f.getName(), flowToPackage.get(f.getId()));
            }
        }

        // Collect JMS/ProcessDirect producers and consumers
        Map<String, List<String[]>> jmsProducers = new LinkedHashMap<>();
        Map<String, List<String[]>> jmsConsumers = new LinkedHashMap<>();
        Map<String, List<String[]>> pdProducers = new LinkedHashMap<>();
        Map<String, List<String[]>> pdConsumers = new LinkedHashMap<>();

        for (IntegrationFlow flow : result.getAllFlows()) {
            if (!flow.isBundleParsed() || flow.getIflowContent() == null) continue;
            String flowName = flow.getName() != null ? flow.getName() : flow.getId();

            for (var adapter : flow.getIflowContent().getAdapters()) {
                String type = adapter.getAdapterType() != null ? adapter.getAdapterType().toLowerCase() : "";
                String address = resolveChainAddress(adapter);
                String dir = adapter.getDirection() != null ? adapter.getDirection() : "";

                if (address.isEmpty()) continue;

                String[] info = { flow.getId(), flowName };
                if (type.contains("jms")) {
                    if ("Receiver".equalsIgnoreCase(dir)) {
                        jmsProducers.computeIfAbsent(address, k -> new ArrayList<>()).add(info);
                    } else if ("Sender".equalsIgnoreCase(dir)) {
                        jmsConsumers.computeIfAbsent(address, k -> new ArrayList<>()).add(info);
                    }
                } else if (type.contains("processdirect")) {
                    if ("Receiver".equalsIgnoreCase(dir)) {
                        pdProducers.computeIfAbsent(address, k -> new ArrayList<>()).add(info);
                    } else if ("Sender".equalsIgnoreCase(dir)) {
                        pdConsumers.computeIfAbsent(address, k -> new ArrayList<>()).add(info);
                    }
                }
            }
        }

        List<FlowChainRow> rows = new ArrayList<>();

        // Match JMS chains
        for (var entry : jmsProducers.entrySet()) {
            String queue = entry.getKey();
            List<String[]> consumers = jmsConsumers.getOrDefault(queue, List.of());
            if (consumers.isEmpty()) continue;
            for (String[] prod : entry.getValue()) {
                for (String[] cons : consumers) {
                    rows.add(new FlowChainRow("JMS", queue,
                            prod[1], flowToPackage.getOrDefault(prod[0], ""),
                            cons[1], flowToPackage.getOrDefault(cons[0], "")));
                }
            }
        }

        // Match ProcessDirect chains
        for (var entry : pdProducers.entrySet()) {
            String addr = entry.getKey();
            List<String[]> consumers = pdConsumers.getOrDefault(addr, List.of());
            if (consumers.isEmpty()) continue;
            for (String[] prod : entry.getValue()) {
                for (String[] cons : consumers) {
                    rows.add(new FlowChainRow("ProcessDirect", addr,
                            prod[1], flowToPackage.getOrDefault(prod[0], ""),
                            cons[1], flowToPackage.getOrDefault(cons[0], "")));
                }
            }
        }

        log.info("Flow chains: {} links detected ({} JMS, {} ProcessDirect)",
                rows.size(),
                rows.stream().filter(r -> "JMS".equals(r.chainType())).count(),
                rows.stream().filter(r -> "ProcessDirect".equals(r.chainType())).count());

        return rows;
    }

    private static String resolveChainAddress(IFlowAdapter adapter) {
        String type = adapter.getAdapterType() != null ? adapter.getAdapterType().toLowerCase() : "";
        Map<String, String> props = adapter.getProperties();
        String dir = adapter.getDirection() != null ? adapter.getDirection() : "";

        if (type.contains("jms")) {
            for (String key : List.of(
                    "Receiver".equalsIgnoreCase(dir) ? "QueueName_outbound" : "QueueName_inbound",
                    "QueueName_outbound", "QueueName_inbound",
                    "Destination", "QueueName", "destination", "queueName")) {
                String val = props.get(key);
                if (val != null && !val.isBlank()) return val;
            }
        }

        if (type.contains("processdirect")) {
            for (String key : List.of("address", "Address", "ProcessDirectAddress")) {
                String val = props.get(key);
                if (val != null && !val.isBlank()) return val;
            }
        }

        if (adapter.getAddress() != null && !adapter.getAddress().isBlank()) {
            return adapter.getAddress();
        }

        for (var entry : props.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("destination") || key.contains("queue") || key.contains("address")) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue();
                }
            }
        }

        return "";
    }
}
