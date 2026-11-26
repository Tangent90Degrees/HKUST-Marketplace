package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.UserRole;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortalSmokeTest {
    private static final String CHAT_TEXT = "Can I pick up the MacBook charger tomorrow?";
    @TempDir Path demoDirectory;

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
                verifyDemoControls(service);
                service.signIn("alice", "password1", UserRole.BUYER);
                Parent buyerPortal = load("buyer/BuyerDashboardView.fxml");
                checkTabs(buyerPortal,
                        "btnBrowse", "btnDeals", "btnHistory", "btnNotifications", "btnProfile");
                sendMessageFromBuyerPortal(buyerPortal);
                service.signIn("charlie", "password1", UserRole.SELLER);
                Parent sellerPortal = load("seller/SellerDashboardView.fxml");
                checkTabs(sellerPortal,
                        "btnListings", "btnCreate", "btnStats", "btnReviews", "btnNotifications", "btnProfile", "btnChats");
                verifySellerCanOpenSaleEdit(sellerPortal);
                verifyBuyerMessageInSellerPortal(sellerPortal);
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

    private static void verifySellerCanOpenSaleEdit(Parent portal) {
        new Scene(portal, 1200, 780);
        ((Button) portal.lookup("#btnListings")).fire();
        portal.applyCss();
        portal.layout();
        Button edit = portal.lookupAll(".button").stream()
                .filter(node -> node instanceof Button button && "Edit".equals(button.getText()))
                .map(Button.class::cast).findFirst().orElseThrow();
        edit.fire();
        portal.applyCss();
        portal.layout();
        assertTrue(portal.lookupAll(".text-field").stream()
                .anyMatch(node -> node instanceof TextField field && !field.getText().isBlank()
                        && "Item name *".equals(field.getPromptText())),
                "The sale edit form should open with its listing title already filled.");
    }

    private static void sendMessageFromBuyerPortal(Parent portal) {
        new Scene(portal, 1200, 780);
        ((Button) portal.lookup("#btnBrowse")).fire();
        TextField search = textField(portal, "Title or keyword");
        search.setText("MacBook Pro Charger");
        search.fireEvent(new ActionEvent());
        withDialog(button(portal, "View details")::fire, details ->
                withDialog(button(details, "Chat")::fire, chat -> {
                    textField(chat, "Write a message").setText(CHAT_TEXT);
                    button(chat, "Send").fire();
                    assertTrue(chatHistory(chat).stream().anyMatch(row -> row.contains(CHAT_TEXT)));
                }));
    }

    private void verifyDemoControls(DummyDataService service) throws Exception {
        Parent normalWelcome = load("WelcomeView.fxml");
        assertFalse(normalWelcome.lookup("#demoControls").isVisible());
        service.openDemoStorage(demoDirectory.resolve("marketplace-demo.data"));
        Parent welcome = load("WelcomeView.fxml");
        new Scene(welcome, 1200, 780);
        assertTrue(welcome.lookup("#demoControls").isVisible());
        Button clear = button(welcome, "Clear demo listings and chat");
        withDialog(clear::fire, pane -> ((Button) pane.lookupButton(ButtonType.CANCEL)).fire());
        assertFalse(service.getLiveListings().isEmpty());
        withDialog(clear::fire, pane -> ((Button) pane.lookupButton(ButtonType.OK)).fire());
        assertTrue(service.getLiveListings().isEmpty());
        withDialog(button(welcome, "Import demo listings and chat")::fire,
                pane -> ((Button) pane.lookupButton(ButtonType.OK)).fire());
        assertFalse(service.getLiveListings().isEmpty());
    }

    private static void verifyBuyerMessageInSellerPortal(Parent portal) {
        ((Button) portal.lookup("#btnChats")).fire();
        portal.applyCss();
        portal.layout();
        assertTrue(portal.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .anyMatch(label -> label.getText().equals("@alice: " + CHAT_TEXT)));
        withDialog(button(portal, "Open chat")::fire, chat ->
                assertTrue(chatHistory(chat).stream().anyMatch(row -> row.contains("@alice") && row.contains(CHAT_TEXT))));
    }

    private static List<String> chatHistory(DialogPane pane) {
        ListView<?> history = (ListView<?>) pane.lookup(".list-view");
        assertNotNull(history);
        return history.getItems().stream().map(Object::toString).toList();
    }

    private static TextField textField(Parent root, String prompt) {
        root.applyCss();
        root.layout();
        return root.lookupAll(".text-field").stream().filter(TextField.class::isInstance).map(TextField.class::cast)
                .filter(field -> prompt.equals(field.getPromptText())).findFirst().orElseThrow();
    }

    private static Button button(Parent root, String title) {
        root.applyCss();
        root.layout();
        return root.lookupAll(".button").stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(control -> title.equals(control.getText())).findFirst().orElseThrow();
    }

    private static void withDialog(Runnable open, Consumer<DialogPane> interact) {
        var result = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            DialogPane pane = null;
            Window dialogWindow = null;
            try {
                pane = Window.getWindows().stream().filter(Window::isShowing).map(window -> window.getScene().getRoot())
                        .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                        .reduce((first, last) -> last).orElseThrow();
                dialogWindow = pane.getScene().getWindow();
                interact.accept(pane);
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                if (dialogWindow != null && dialogWindow.isShowing()) {
                    ButtonType cancel = pane.getButtonTypes().stream()
                            .filter(type -> type.getButtonData().isCancelButton()).findFirst().orElseThrow();
                    ((Button) pane.lookupButton(cancel)).fire();
                }
            }
        });
        open.run();
        assertTrue(result.isDone(), "The expected dialog did not open");
        result.join();
    }
}
