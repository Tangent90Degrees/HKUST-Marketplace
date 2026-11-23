package com.hkust.marketplace.utils;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

/** Shared seller and administrator notification list with read state and current record links. */
public final class NotificationBoard {
    private NotificationBoard() {}

    public static ScrollPane current(Runnable refresh) {
        var service = DummyDataService.getInstance();
        VBox entries = new VBox(12);
        entries.setPadding(new Insets(4));
        try {
            var notices = service.ownNotifications();
            if (notices.isEmpty()) {
                entries.getChildren().add(new Label("No notifications."));
            }
            for (var notice : notices) {
                Button open = new Button((notice.isRead() ? "" : "Unread · ") + notice.getTitle());
                open.setOnAction(event -> open(notice.getId(), refresh));
                Label summary = new Label(notice.getMessage());
                summary.setWrapText(true);
                Label time = new Label(notice.getCategory() + " · "
                        + notice.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
                VBox item = new VBox(4, open, summary, time);
                item.getStyleClass().add("notif-item");
                entries.getChildren().add(item);
            }
        } catch (IllegalStateException | SecurityException failure) {
            entries.getChildren().add(new Label(failure.getMessage()));
        }
        ScrollPane scroll = new ScrollPane(entries);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static void open(String id, Runnable refresh) {
        var service = DummyDataService.getInstance();
        try {
            var notice = service.readOwnNotice(id);
            Alert details = new Alert(Alert.AlertType.INFORMATION, notice.getMessage(), ButtonType.CLOSE);
            details.setHeaderText(notice.getTitle());
            if (notice.getReference() == null) {
                details.showAndWait();
            } else {
                ButtonType link = new ButtonType("Open related record");
                details.getButtonTypes().add(link);
                if (details.showAndWait().orElse(ButtonType.CLOSE) == link) {
                    new Alert(Alert.AlertType.INFORMATION, service.noticeRecord(id)).showAndWait();
                }
            }
        } catch (IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
        refresh.run();
    }
}
