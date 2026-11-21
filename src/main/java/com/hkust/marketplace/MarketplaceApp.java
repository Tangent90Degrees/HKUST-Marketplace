package com.hkust.marketplace;

import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.DummyDataService;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.nio.file.Path;

/**
 * HKUST Marketplace - Main Application Entry Point
 * Opens local marketplace data and starts the JavaFX portal selector.
 */
public class MarketplaceApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            Path dataFile = Path.of(System.getProperty("user.home"), ".hkust-marketplace", "marketplace.data");
            DummyDataService.getInstance().openLocalStorage(dataFile);
        } catch (IllegalStateException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            return;
        }
        SceneManager.init(primaryStage);
        SceneManager.showWelcome();
        primaryStage.setTitle("HKUST Marketplace");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
