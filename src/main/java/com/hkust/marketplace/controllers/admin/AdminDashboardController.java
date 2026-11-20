package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdminDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Admin: " + user.getFullName());
        currentActiveBtn = btnReview;
        showReview();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showReview() {
        setActive(btnReview, "Listing Review & Approval");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        List<Listing> pending = DummyDataService.getInstance().getPendingListings();
        if (pending.isEmpty()) {
            Label empty = new Label("No listings awaiting review.");
            empty.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 15px;");
            root.getChildren().add(empty);
        } else {
            for (Listing l : pending) {
                root.getChildren().add(createPendingCard(l));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createPendingCard(Listing l) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(16));

        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Label meta = new Label(String.format("Seller: %s (@%s)  ·  %s  ·  %s  ·  %s",
                l.getSellerFullName(), l.getSellerUsername(),
                l.getCategory(), l.getTypeDisplay(), l.getMeetupLocation()));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label submitted = new Label("Submitted: " +
                l.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) + " · Pending Review");
        submitted.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        HBox actions = new HBox(10);
        Button approve = new Button("Approve");
        approve.getStyleClass().addAll("btn-success", "btn-sm");
        approve.setOnAction(e -> {
            Alert conf = new Alert(Alert.AlertType.CONFIRMATION, "Approve this listing?");
            conf.showAndWait().ifPresent(r -> {
                if (r == ButtonType.OK) {
                    review(l, true, null);
                }
            });
        });

        Button reject = new Button("Reject");
        reject.getStyleClass().addAll("btn-danger", "btn-sm");
        reject.setOnAction(e -> {
            TextInputDialog reasonDialog = new TextInputDialog();
            reasonDialog.setTitle("Rejection Reason");
            reasonDialog.setHeaderText("Please provide a reason for rejection");
            reasonDialog.setContentText("Reason:");
            reasonDialog.showAndWait().ifPresent(reason -> {
                if (reason.isBlank()) {
                    new Alert(Alert.AlertType.WARNING, "Reason is required.").showAndWait();
                    return;
                }
                if (new Alert(Alert.AlertType.CONFIRMATION, "Reject this listing?")
                        .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    review(l, false, reason);
                }
            });
        });

        actions.getChildren().addAll(approve, reject);
        card.getChildren().addAll(title, meta, submitted);
        FlowPane photos = new FlowPane(8, 8);
        for (byte[] bytes : l.getPhotos()) {
            Image image = new Image(new ByteArrayInputStream(bytes), 160, 120, true, true);
            if (!image.isError()) {
                photos.getChildren().add(new ImageView(image));
            }
        }
        if (!photos.getChildren().isEmpty()) {
            card.getChildren().add(photos);
        }
        card.getChildren().add(actions);
        return card;
    }

    private void review(Listing listing, boolean approve, String reason) {
        try {
            DummyDataService.getInstance().reviewListing(listing.getId(), approve, reason);
            new Alert(Alert.AlertType.INFORMATION, approve ? "Listing approved." : "Listing rejected.").showAndWait();
            showReview();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "Manage All Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button addBtn = new Button("+ Add Listing (on behalf of seller)");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> editAdminListing(null));
        root.getChildren().add(addBtn);

        for (Listing l : DummyDataService.getInstance().getAllListings()) {
            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label t = new Label(l.getTitle());
            t.setStyle("-fx-font-weight: bold;");
            Label m = new Label(l.getSellerFullName() + " · " + l.getTypeDisplay() + " · " + l.getStatus());
            m.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
            info.getChildren().addAll(t, m);

            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(e -> editAdminListing(l));

            Button remove = new Button("Remove");
            remove.getStyleClass().addAll("btn-danger", "btn-sm");
            remove.setDisable(l.getStatus() != ListingStatus.LIVE);
            remove.setOnAction(e -> {
                TextInputDialog reason = new TextInputDialog();
                reason.setTitle("Take down listing");
                reason.setHeaderText("Enter the reason for taking down " + l.getTitle());
                reason.showAndWait().ifPresent(value -> {
                    if (confirm("Take down this live listing and notify buyers with open offers?")) {
                        runAdminAction(() -> DummyDataService.getInstance().adminTakeDown(l.getId(), value),
                                this::showAllListings);
                    }
                });
            });

            row.getChildren().addAll(info, edit, remove);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void editAdminListing(Listing original) {
        var service = DummyDataService.getInstance();
        Dialog<ButtonType> dialog = modal(original == null ? "Publish for a seller" : "Edit listing");
        ComboBox<String> seller = new ComboBox<>(FXCollections.observableArrayList(service.getAllUsers().stream()
                .filter(user -> user.getRole() == UserRole.SELLER && user.isActive()).map(User::getUsername).toList()));
        ComboBox<ListingType> type = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        TextField name = new TextField(original == null ? "" : original.getTitle());
        TextArea description = new TextArea(original == null ? "" : original.getDescription());
        description.setPrefRowCount(2);
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        ComboBox<String> condition = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CONDITIONS));
        ComboBox<String> location = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        TextField price = new TextField();
        TextField duration = new TextField();
        List<byte[]> photos = new ArrayList<>();
        Label photoCount = new Label();
        if (original != null) {
            type.setValue(original.getType());
            category.setValue(original.getCategory());
            condition.setValue(original.getCondition());
            location.setValue(original.getMeetupLocation());
            price.setText(Objects.toString(original.getPrice(), ""));
            duration.setText(Objects.toString(original.getLendingSeconds(), ""));
            photos.addAll(original.getPhotos());
        }
        photoCount.setText(photos.size() + " photos");
        Button upload = new Button("Choose up to six photos");
        upload.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif"));
            List<File> files = chooser.showOpenMultipleDialog(dialog.getDialogPane().getScene().getWindow());
            if (files == null) {
                return;
            }
            runAdminAction(() -> {
                if (files.size() > 6) {
                    throw new IllegalArgumentException("Choose up to six photos.");
                }
                List<byte[]> loaded = new ArrayList<>();
                for (File file : files) {
                    try {
                        if (Files.size(file.toPath()) > 5 * 1024 * 1024) {
                            throw new IllegalArgumentException("Choose photos up to 5 MB each.");
                        }
                        byte[] bytes = Files.readAllBytes(file.toPath());
                        if (new Image(new ByteArrayInputStream(bytes), 128, 128, true, true).isError()) {
                            throw new IllegalArgumentException("Cannot read photo: " + file.getName());
                        }
                        loaded.add(bytes);
                    } catch (IOException failure) {
                        throw new IllegalStateException("Could not read photo: " + file.getName(), failure);
                    }
                }
                photos.clear();
                photos.addAll(loaded);
                photoCount.setText(photos.size() + " photos");
            }, () -> {});
        });
        VBox fields = new VBox(6, new Label("Listing type"), type);
        if (original == null) {
            fields.getChildren().addAll(new Label("Seller"), seller);
        }
        HBox sale = new HBox(8, new Label("Price (HK$)"), price);
        HBox loan = new HBox(8, new Label("Lending duration (seconds)"), duration);
        sale.visibleProperty().bind(type.valueProperty().isEqualTo(ListingType.SELL));
        loan.visibleProperty().bind(type.valueProperty().isEqualTo(ListingType.LEND));
        sale.managedProperty().bind(sale.visibleProperty());
        loan.managedProperty().bind(loan.visibleProperty());
        fields.getChildren().addAll(new Label("Item name"), name, new Label("Description"), description,
                new Label("Category"), category, new Label("Condition"), condition, new Label("Meet-up location"),
                location, sale, loan, new HBox(8, upload, photoCount));
        dialog.getDialogPane().setContent(fields);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        runAdminAction(() -> {
            Listing draft = new Listing();
            draft.setType(type.getValue());
            draft.setTitle(name.getText().strip());
            draft.setDescription(description.getText());
            draft.setCategory(category.getValue());
            draft.setCondition(condition.getValue());
            draft.setMeetupLocation(location.getValue());
            draft.setPhotos(photos);
            if (draft.getType() == ListingType.SELL) {
                draft.setPrice(Double.valueOf(price.getText()));
            } else if (draft.getType() == ListingType.LEND) {
                draft.setLendingSeconds(Long.valueOf(duration.getText()));
            }
            if (original == null) {
                service.adminCreateListing(seller.getValue(), draft);
            } else if (confirm("Apply this edit? Unaccepted proposals will close and their buyers will be notified.")) {
                service.adminEditListing(original.getId(), draft);
            }
        }, this::showAllListings);
    }

    @FXML
    private void showUsers() {
        setActive(btnUsers, "Manage All Users");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        for (User u : DummyDataService.getInstance().getAllUsers()) {
            if (u.getRole() == UserRole.ADMIN) continue;

            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(3);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label name = new Label(u.getFullName() + " (@" + u.getUsername() + ")");
            name.setStyle("-fx-font-weight: bold;");
            Label role = new Label(u.getRole().name() + (u.isActive() ? " · Active" : " · Deactivated"));
            role.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
            info.getChildren().addAll(name, role);

            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(e -> editUser(u));

            Button toggle = new Button("Deactivate");
            toggle.getStyleClass().addAll("btn-danger", "btn-sm");
            toggle.setDisable(!u.isActive());
            toggle.setOnAction(e -> moderateUser(u, false));

            Button delete = new Button("Delete");
            delete.getStyleClass().addAll("btn-danger", "btn-sm");
            delete.setOnAction(e -> moderateUser(u, true));

            row.getChildren().addAll(info, edit, toggle, delete);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void moderateUser(User user, boolean delete) {
        String action = delete ? "Permanently remove" : "Deactivate";
        if (confirm(action + " @" + user.getUsername() + "? Access and pending proposals will close. "
                + "Relevant seller listings will be removed. Past records are retained.")) {
            runAdminAction(() -> DummyDataService.getInstance().moderateAccount(user.getUsername(), delete), this::showUsers);
        }
    }

    private void editUser(User user) {
        Dialog<ButtonType> dialog = modal("Edit account");
        TextField username = new TextField(user.getUsername());
        TextField name = new TextField(user.getFullName());
        TextField residence = new TextField(user.getResidenceHall());
        TextField bio = new TextField(user.getBio());
        ComboBox<String> location = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        location.setValue(user.getPreferredMeetup());
        VBox fields = new VBox(8, new Label("Username"), username, new Label("Full name"), name);
        if (user.getRole() == UserRole.BUYER) {
            fields.getChildren().addAll(new Label("Residence"), residence);
        } else {
            fields.getChildren().addAll(new Label("Bio"), bio, new Label("Preferred meet-up location"), location);
        }
        dialog.getDialogPane().setContent(fields);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            runAdminAction(() -> DummyDataService.getInstance().adminEditUser(user.getUsername(), username.getText(),
                    name.getText(), residence.getText(), bio.getText(), location.getValue()), this::showUsers);
        }
    }

    private static Dialog<ButtonType> modal(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog;
    }

    private static boolean confirm(String prompt) {
        return new Alert(Alert.AlertType.CONFIRMATION, prompt).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static void runAdminAction(Runnable action, Runnable refresh) {
        try {
            action.run();
            refresh.run();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    @FXML
    private void showDeals() {
        setActive(btnDeals, "All Deals Record");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search by item / user...");
        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList("All Types", "Sell", "Lend", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(search, typeFilter);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        String[] headers = {"Item", "Buyer", "Seller", "Type", "Status", "Date"};
        for (int i = 0; i < headers.length; i++) {
            final int idx = i;
            TableColumn<String[], String> col = new TableColumn<>(headers[i]);
            col.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[idx]));
            table.getColumns().add(col);
        }
        table.setItems(FXCollections.observableArrayList(
                new String[]{"Calculus Textbook", "alice", "charlie", "Sell", "Accepted", "2026-01-15"},
                new String[]{"USB Hub", "bob", "diana", "Giveaway", "Completed", "2025-12-01"}
        ));
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        TextField userFilter = new TextField();
        userFilter.setPromptText("Filter by seller...");
        ComboBox<String> scoreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All scores", "5 stars", "4 stars", "3 stars", "2 stars", "1 star"));
        scoreFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(userFilter, scoreFilter);
        root.getChildren().add(filters);

        for (Review r : DummyDataService.getInstance().getAllReviews()) {
            HBox row = new HBox(12);
            row.getStyleClass().add("card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));

            VBox info = new VBox(4);
            HBox.setHgrow(info, Priority.ALWAYS);
            Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()) +
                    "  @" + r.getSellerUsername() + " by @" + r.getBuyerUsername());
            stars.setStyle("-fx-font-weight: bold;");
            Label comment = new Label(r.getComment());
            comment.setWrapText(true);
            comment.setStyle("-fx-text-fill: #5a5a7a;");
            info.getChildren().addAll(stars, comment);

            Button hide = new Button(r.isHidden() ? "Unhide" : "Hide");
            hide.getStyleClass().addAll("btn-outline", "btn-sm");
            hide.setOnAction(e -> {
                r.setHidden(!r.isHidden());
                showRatings();
            });
            row.getChildren().addAll(info, hide);
            root.getChildren().add(row);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Admin Notifications");
        contentPane.getChildren().clear();
        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        List<Notification> notifs = DummyDataService.getInstance().getNotificationsFor("admin");
        if (notifs.isEmpty()) {
            root.getChildren().add(new Label("No notifications."));
        } else {
            for (Notification n : notifs) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                Label t = new Label(n.getTitle());
                t.setStyle("-fx-font-weight: bold;");
                Label m = new Label(n.getMessage());
                m.setWrapText(true);
                m.setStyle("-fx-text-fill: #5a5a7a;");
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(t, m, meta);
                root.getChildren().add(item);
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(12);
        form.setMaxWidth(400);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        Label titleLbl = new Label("Admin Profile");
        titleLbl.getStyleClass().add("section-title");
        form.getChildren().add(titleLbl);

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField pass = new PasswordField();
        pass.setPromptText("New password (optional)");
        PasswordField confirmation = new PasswordField();
        Button save = new Button("Save");
        save.getStyleClass().add("btn-success");
        save.setOnAction(e -> {
            try {
                DummyDataService.getInstance().updateAdminProfile(name.getText(), pass.getText(), confirmation.getText());
                pass.clear();
                confirmation.clear();
                welcomeLabel.setText("Admin: " + user.getFullName());
                new Alert(Alert.AlertType.INFORMATION, "Profile updated successfully.").showAndWait();
            } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            }
        });
        form.getChildren().addAll(new Label("Full Name"), name, new Label("New Password"), pass,
                new Label("Confirm Password"), confirmation, save);
        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
