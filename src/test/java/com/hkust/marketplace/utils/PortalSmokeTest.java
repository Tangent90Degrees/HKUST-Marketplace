package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.UserRole;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PortalSmokeTest {
    @Test
    @EnabledIfSystemProperty(named = "marketplace.uiSmoke", matches = "true")
    void loadsWelcomeAndAllRoleViewsOnJavaFxThread() throws Exception {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        Platform.startup(() -> Platform.runLater(() -> {
            try {
                for (String view : List.of("WelcomeView.fxml", "buyer/BuyerLoginView.fxml",
                        "buyer/BuyerRegisterView.fxml", "seller/SellerLoginView.fxml",
                        "seller/SellerRegisterView.fxml", "admin/AdminLoginView.fxml")) {
                    load(view);
                }
                var service = DummyDataService.getInstance();
                service.signIn("alice", "password1", UserRole.BUYER);
                checkTabs(load("buyer/BuyerDashboardView.fxml"),
                        "btnBrowse", "btnDeals", "btnHistory", "btnNotifications", "btnProfile");
                service.signIn("charlie", "password1", UserRole.SELLER);
                checkTabs(load("seller/SellerDashboardView.fxml"),
                        "btnListings", "btnCreate", "btnStats", "btnReviews", "btnNotifications", "btnProfile");
                service.signIn("admin", "admin123", UserRole.ADMIN);
                checkTabs(load("admin/AdminDashboardView.fxml"), "btnReview", "btnAllListings", "btnUsers",
                        "btnDeals", "btnRatings", "btnNotifications", "btnProfile");
                finished.complete(null);
            } catch (Throwable failure) {
                finished.completeExceptionally(failure);
            } finally {
                SessionManager.logout();
                Platform.exit();
            }
        }));
        finished.get(20, TimeUnit.SECONDS);
    }

    private static Parent load(String path) throws Exception {
        var resource = PortalSmokeTest.class.getResource("/fxml/" + path);
        assertNotNull(resource, path);
        Parent root = FXMLLoader.load(resource);
        assertNotNull(root, path);
        return root;
    }

    private static void checkTabs(Parent portal, String... ids) {
        for (String id : ids) {
            Button tab = (Button) portal.lookup("#" + id);
            assertNotNull(tab, id);
            tab.fire();
        }
    }
}
