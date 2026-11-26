package com.hkust.marketplace.controllers;

import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class WelcomeController {
    @FXML private HBox demoControls;
    @FXML private Label demoStatus;

    @FXML
    private void initialize() {
        boolean demo = DummyDataService.getInstance().isDemoMode();
        demoControls.setVisible(demo);
        demoControls.setManaged(demo);
        demoStatus.setVisible(demo);
        demoStatus.setManaged(demo);
    }

    @FXML
    private void importDemoData() {
        confirmDemoChange("Replace the current demo listings and chat with the bundled samples?",
                DummyDataService.getInstance()::importDemoData, "Demo listings and chat imported.");
    }

    @FXML
    private void clearDemoData() {
        confirmDemoChange("Clear demo listings, chat, deals, reviews, reports, and notifications? Accounts will be kept.",
                DummyDataService.getInstance()::clearDemoData, "Demo listings and chat cleared.");
    }

    private void confirmDemoChange(String prompt, Runnable action, String success) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION, prompt, ButtonType.OK, ButtonType.CANCEL);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                action.run();
                demoStatus.setText(success);
            } catch (IllegalStateException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void goBuyer() {
        System.out.println("[Welcome] goBuyer()");
        SceneManager.showBuyerLogin();
    }

    @FXML
    private void goSeller() {
        System.out.println("[Welcome] goSeller()");
        SceneManager.showSellerLogin();
    }

    @FXML
    private void goAdmin() {
        System.out.println("[Welcome] goAdmin()");
        SceneManager.showAdminLogin();
    }
}
