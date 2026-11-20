package com.hkust.marketplace;

import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.DummyDataService;
import javafx.application.Application;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.nio.file.Path;

/**
 * HKUST Marketplace - Main Application Entry Point
 * Opens local marketplace data and starts the JavaFX portal selector.
 */
public class MarketplaceApp extends Application {
    private Timeline borrowingUpdates;

    @Override
    public void start(Stage primaryStage) {
        try {
            Path defaultFile = Path.of(System.getProperty("user.home"), ".hkust-marketplace", "marketplace.data");
            Path dataFile = Path.of(System.getProperty("marketplace.data", defaultFile.toString()));
            DummyDataService.getInstance().openLocalStorage(dataFile);
        } catch (IllegalStateException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            return;
        }
        SceneManager.init(primaryStage);
        SceneManager.showWelcome();
        borrowingUpdates = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            try {
                DummyDataService.getInstance().advanceBorrowings();
            } catch (IllegalStateException failure) {
                System.getLogger(MarketplaceApp.class.getName()).log(System.Logger.Level.WARNING,
                        "Could not save borrowing updates. The app will retry.", failure);
            }
        }));
        borrowingUpdates.setCycleCount(Timeline.INDEFINITE);
        borrowingUpdates.play();
        primaryStage.setTitle("HKUST Marketplace");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (borrowingUpdates != null) {
            borrowingUpdates.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
