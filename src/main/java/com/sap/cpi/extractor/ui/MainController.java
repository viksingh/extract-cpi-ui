package com.sap.cpi.extractor.ui;

import com.sap.cpi.extractor.config.CpiConfiguration;
import com.sap.cpi.extractor.export.CsvExporter;
import com.sap.cpi.extractor.export.ExcelExporter;
import com.sap.cpi.extractor.export.JsonExporter;
import com.sap.cpi.extractor.model.*;
import com.sap.cpi.extractor.service.CpiApiService;
import com.sap.cpi.extractor.service.CpiHttpClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // Connection fields
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

    // Export settings
    @FXML private ComboBox<String> exportFormatCombo;
    @FXML private TextField outputDirField;
    @FXML private TextField filenamePrefixField;

    // Results
    @FXML private TabPane resultsTabPane;
    @FXML private TextArea summaryTextArea;
    @FXML private TableView<IntegrationPackage> packagesTable;
    @FXML private TableView<IntegrationFlow> flowsTable;
    @FXML private TableView<ValueMapping> valueMapsTable;
    @FXML private TableView<ConfigRow> configsTable;
    @FXML private TableView<RuntimeArtifact> runtimeTable;

    // Log + progress
    @FXML private TextArea logTextArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Button extractButton;

    @FXML
    public void initialize() {
        // Auth type combo
        authTypeCombo.setItems(FXCollections.observableArrayList("OAuth2", "Basic"));
        authTypeCombo.setValue("OAuth2");

        // Export format combo
        exportFormatCombo.setItems(FXCollections.observableArrayList("Excel (.xlsx)", "CSV", "JSON", "All Formats"));
        exportFormatCombo.setValue("Excel (.xlsx)");

        // Wire up log appender
        TextAreaLogAppender.setTextArea(logTextArea);

        // Initialize table columns
        initPackagesTable();
        initFlowsTable();
        initValueMapsTable();
        initConfigsTable();
        initRuntimeTable();
    }

    // =========================================================================
    // Action Handlers
    // =========================================================================

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
    private void onLoadConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Configuration File");
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
                case "csv" -> exportFormatCombo.setValue("CSV");
                case "json" -> exportFormatCombo.setValue("JSON");
                case "all" -> exportFormatCombo.setValue("All Formats");
                default -> exportFormatCombo.setValue("Excel (.xlsx)");
            }

            setFieldIfPresent(props, "export.output.dir", outputDirField);
            setFieldIfPresent(props, "export.filename.prefix", filenamePrefixField);

            // Extraction options
            extractPackagesCb.setSelected(getBool(props, "extract.packages", true));
            extractFlowsCb.setSelected(getBool(props, "extract.flows", true));
            extractValueMappingsCb.setSelected(getBool(props, "extract.valuemappings", true));
            extractConfigurationsCb.setSelected(getBool(props, "extract.configurations", true));
            extractRuntimeCb.setSelected(getBool(props, "extract.runtime.status", true));

            appendLog("Configuration loaded from: " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Failed to load config", e.getMessage());
        }
    }

    @FXML
    private void onSaveConfig() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Configuration File");
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
            appendLog("Configuration saved to: " + file.getAbsolutePath());
        } catch (IOException e) {
            showError("Failed to save config", e.getMessage());
        }
    }

    @FXML
    private void onBrowseOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Directory");
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

    @FXML
    private void onExtract() {
        // Validate required fields
        if (tenantUrlField.getText().isBlank()) {
            showError("Validation Error", "Tenant URL is required.");
            return;
        }

        extractButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Starting extraction...");
        logTextArea.clear();

        Task<ExtractionResult> task = new Task<>() {
            @Override
            protected ExtractionResult call() throws Exception {
                // Build configuration from form
                Properties props = buildPropertiesFromForm();

                // Write to temp file for CpiConfiguration to load
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
                    result = apiService.extractAll();
                }

                // Export
                String format = getExportFormat();
                String outputDir = props.getProperty("export.output.dir", "./output");
                String prefix = props.getProperty("export.filename.prefix", "cpi_artifacts");

                updateMessage("Exporting results...");

                switch (format) {
                    case "xlsx" -> new ExcelExporter().export(result, outputDir, prefix);
                    case "csv" -> new CsvExporter().export(result, outputDir, prefix);
                    case "json" -> new JsonExporter().export(result, outputDir, prefix);
                    case "all" -> {
                        new ExcelExporter().export(result, outputDir, prefix);
                        new CsvExporter().export(result, outputDir, prefix);
                        new JsonExporter().export(result, outputDir, prefix);
                    }
                }

                // Clean up temp file
                Files.deleteIfExists(tempConfig);

                return result;
            }
        };

        task.messageProperty().addListener((obs, oldMsg, newMsg) ->
                Platform.runLater(() -> progressLabel.setText(newMsg)));

        task.setOnSucceeded(event -> {
            ExtractionResult result = task.getValue();
            Platform.runLater(() -> {
                progressBar.setProgress(1.0);
                progressLabel.setText("Extraction complete!");
                extractButton.setDisable(false);
                populateResults(result);
                appendLog("Extraction and export completed successfully.");
            });
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setText("Extraction failed.");
                extractButton.setDisable(false);
                appendLog("ERROR: " + ex.getMessage());
                showError("Extraction Failed", ex.getMessage());
            });
        });

        Thread thread = new Thread(task, "cpi-extraction-thread");
        thread.setDaemon(true);
        thread.start();
    }

    // =========================================================================
    // Results Population
    // =========================================================================

    private void populateResults(ExtractionResult result) {
        // Summary tab
        summaryTextArea.setText(result.getSummary());

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

        // Switch to Summary tab
        resultsTabPane.getSelectionModel().selectFirst();
    }

    // =========================================================================
    // Table Initialization
    // =========================================================================

    private void initPackagesTable() {
        addColumn(packagesTable, "Package ID", "id");
        addColumn(packagesTable, "Name", "name");
        addColumn(packagesTable, "Description", "description");
        addColumn(packagesTable, "Version", "version");
        addColumn(packagesTable, "Vendor", "vendor");
        addColumn(packagesTable, "Mode", "mode");
        addColumn(packagesTable, "Created By", "createdBy");
        addColumn(packagesTable, "Creation Date", "creationDate");
        addColumn(packagesTable, "Modified By", "modifiedBy");
        addColumn(packagesTable, "Modified Date", "modifiedDate");
        packagesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void initFlowsTable() {
        addColumn(flowsTable, "Flow ID", "id");
        addColumn(flowsTable, "Name", "name");
        addColumn(flowsTable, "Package ID", "packageId");
        addColumn(flowsTable, "Version", "version");
        addColumn(flowsTable, "Sender", "sender");
        addColumn(flowsTable, "Receiver", "receiver");
        addColumn(flowsTable, "Runtime Status", "runtimeStatus");
        addColumn(flowsTable, "Deployed Version", "deployedVersion");
        addColumn(flowsTable, "Created By", "createdBy");
        addColumn(flowsTable, "Modified By", "modifiedBy");
        flowsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void initValueMapsTable() {
        addColumn(valueMapsTable, "ID", "id");
        addColumn(valueMapsTable, "Name", "name");
        addColumn(valueMapsTable, "Description", "description");
        addColumn(valueMapsTable, "Package ID", "packageId");
        addColumn(valueMapsTable, "Version", "version");
        addColumn(valueMapsTable, "Created By", "createdBy");
        addColumn(valueMapsTable, "Modified By", "modifiedBy");
        addColumn(valueMapsTable, "Runtime Status", "runtimeStatus");
        valueMapsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    @SuppressWarnings("unchecked")
    private void initConfigsTable() {
        TableColumn<ConfigRow, String> artifactIdCol = new TableColumn<>("Artifact ID");
        artifactIdCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().artifactId()));

        TableColumn<ConfigRow, String> artifactNameCol = new TableColumn<>("Artifact Name");
        artifactNameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().artifactName()));

        TableColumn<ConfigRow, String> keyCol = new TableColumn<>("Parameter Key");
        keyCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().parameterKey()));

        TableColumn<ConfigRow, String> valueCol = new TableColumn<>("Parameter Value");
        valueCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().parameterValue()));

        TableColumn<ConfigRow, String> typeCol = new TableColumn<>("Data Type");
        typeCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().dataType()));

        configsTable.getColumns().addAll(artifactIdCol, artifactNameCol, keyCol, valueCol, typeCol);
        configsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private void initRuntimeTable() {
        addColumn(runtimeTable, "Artifact ID", "id");
        addColumn(runtimeTable, "Name", "name");
        addColumn(runtimeTable, "Type", "type");
        addColumn(runtimeTable, "Version", "version");
        addColumn(runtimeTable, "Status", "status");
        addColumn(runtimeTable, "Deployed By", "deployedBy");
        addColumn(runtimeTable, "Deployed On", "deployedOn");
        addColumn(runtimeTable, "Error Info", "errorInformation");
        runtimeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
    }

    private <T> void addColumn(TableView<T> table, String title, String property) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(new PropertyValueFactory<>(property));
        table.getColumns().add(col);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

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

        props.setProperty("extract.packages", String.valueOf(extractPackagesCb.isSelected()));
        props.setProperty("extract.flows", String.valueOf(extractFlowsCb.isSelected()));
        props.setProperty("extract.valuemappings", String.valueOf(extractValueMappingsCb.isSelected()));
        props.setProperty("extract.configurations", String.valueOf(extractConfigurationsCb.isSelected()));
        props.setProperty("extract.runtime.status", String.valueOf(extractRuntimeCb.isSelected()));

        return props;
    }

    private String getExportFormat() {
        String selected = exportFormatCombo.getValue();
        if (selected == null) return "xlsx";
        return switch (selected) {
            case "CSV" -> "csv";
            case "JSON" -> "json";
            case "All Formats" -> "all";
            default -> "xlsx";
        };
    }

    private void setFieldIfPresent(Properties props, String key, TextField field) {
        String val = props.getProperty(key);
        if (val != null && !val.isBlank()) {
            field.setText(val);
        }
    }

    private void setPasswordIfPresent(Properties props, String key, PasswordField field) {
        String val = props.getProperty(key);
        if (val != null && !val.isBlank()) {
            field.setText(val);
        }
    }

    private boolean getBool(Properties props, String key, boolean defaultVal) {
        String val = props.getProperty(key);
        return val != null ? Boolean.parseBoolean(val) : defaultVal;
    }

    private void appendLog(String message) {
        Platform.runLater(() -> {
            logTextArea.appendText(message + "\n");
            logTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

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
    // Config row record for flattened configuration display
    // =========================================================================

    public record ConfigRow(String artifactId, String artifactName,
                            String parameterKey, String parameterValue, String dataType) {}
}
