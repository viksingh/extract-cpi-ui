package com.sakiv.cpi.extractor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class CpiExtractorFxApp extends Application {

    // @author Vikas Singh | Created: 2026-02-01
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        primaryStage.setTitle("SAP CPI Artifact Extractor");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(750);
        primaryStage.setWidth(1100);
        primaryStage.setHeight(800);
        primaryStage.show();
    }

    // @author Vikas Singh | Created: 2026-02-01
    public static void main(String[] args) {
        launch(args);
    }
}
