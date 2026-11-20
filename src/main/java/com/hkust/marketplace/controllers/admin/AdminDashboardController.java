package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.NotificationBoard;
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
        var service = DummyDataService.getInstance();
        TextField search = new TextField();
        search.setPromptText("Item, buyer or seller");
        ComboBox<ListingType> type = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        type.setPromptText("Any type");
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("Accepted", "Completed", "Returned"));
        status.setPromptText("Any status");
        DatePicker from = new DatePicker();
        from.setPromptText("From start date");
        DatePicker to = new DatePicker();
        to.setPromptText("Through start date");
        Label feedback = new Label();
        TableView<String[]> table = recordTable("Item", "Buyer", "Seller", "Deal type", "Start (HKT)", "Return / completion", "Status");
        Runnable refresh = () -> {
            try {
                var matches = service.adminDealRecords(search.getText(), type.getValue(), status.getValue(), from.getValue(), to.getValue());
                table.getItems().setAll(matches.stream().map(deal -> new String[]{deal.getListingTitle(),
                        deal.getBuyerUsername(), deal.getSellerUsername(), deal.displayType(),
                        displayTime(deal.getStartedAt()), displayTime(deal.getCompletedAt()), deal.getStatus()}).toList());
                feedback.setText(matches.isEmpty() ? "No deal records match." : "");
            } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
                feedback.setText(failure.getMessage());
            }
        };
        Button apply = new Button("Apply");
        apply.setOnAction(event -> refresh.run());
        Button clear = new Button("Clear");
        clear.setOnAction(event -> {
            search.clear();
            type.setValue(null);
            status.setValue(null);
            from.setValue(null);
            to.setValue(null);
            refresh.run();
        });
        VBox root = new VBox(12, new FlowPane(8, 8, search, type, status, from, to, apply, clear), feedback, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        contentPane.getChildren().setAll(root);
        refresh.run();
    }

    private static TableView<String[]> recordTable(String... headers) {
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        for (int index = 0; index < headers.length; index++) {
            int columnIndex = index;
            TableColumn<String[], String> column = new TableColumn<>(headers[index]);
            column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue()[columnIndex]));
            table.getColumns().add(column);
        }
        return table;
    }

    private static String displayTime(java.time.LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"));
    }

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();
        var service = DummyDataService.getInstance();
        TextField user = new TextField();
        user.setPromptText("Buyer or seller username");
        ComboBox<Integer> score = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        score.setPromptText("Any score");
        DatePicker from = new DatePicker();
        from.setPromptText("From submission date");
        DatePicker to = new DatePicker();
        to.setPromptText("Through submission date");
        Label feedback = new Label();
        VBox cards = new VBox(10);
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        Runnable refresh = () -> renderRatings(service, user.getText(), score.getValue(),
                from.getValue(), to.getValue(), cards, feedback);
        Button apply = new Button("Apply");
        apply.setOnAction(event -> refresh.run());
        Button clear = new Button("Clear");
        clear.setOnAction(event -> {
            user.clear();
            score.setValue(null);
            from.setValue(null);
            to.setValue(null);
            refresh.run();
        });
        VBox root = new VBox(12, new FlowPane(8, 8, user, score, from, to, apply, clear), feedback, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        contentPane.getChildren().setAll(root);
        refresh.run();
    }

    private void renderRatings(DummyDataService service, String user, Integer score,
                               java.time.LocalDate from, java.time.LocalDate to, VBox cards, Label feedback) {
        List<Review> matches;
        try {
            matches = service.adminRatings(user, score, from, to);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            feedback.setText(failure.getMessage());
            return;
        }
        cards.getChildren().clear();
        feedback.setText(matches.isEmpty() ? "No ratings match." : "");
        for (Review review : matches) {
            Label text = new Label(Objects.toString(review.getComment(), ""));
            text.setWrapText(true);
            VBox card = new VBox(6, new Label(review.getListingTitle() + " / " + review.getRating() + " of 5"),
                    new Label("Buyer @" + review.getBuyerUsername() + " / Seller @" + review.getSellerUsername()
                            + " / " + displayTime(review.getCreatedAt())),
                    new Label(review.isHidden() ? "Hidden" : "Visible"), text);
            card.getStyleClass().add("card");
            if (review.isFlagged()) {
                card.getChildren().add(new Label("Flagged by seller"));
            }
            if (review.getSellerReply() != null) {
                card.getChildren().add(new Label("Seller reply: " + review.getSellerReply()));
            }
            if (!review.isHidden()) {
                Button hide = new Button("Hide rating");
                hide.setOnAction(event -> {
                    if (confirm("Hide this rating and reply from public views?")) {
                        runAdminAction(() -> service.hideRating(review.getId()),
                                () -> renderRatings(service, user, score, from, to, cards, feedback));
                    }
                });
                card.getChildren().add(hide);
            }
            cards.getChildren().add(card);
        }
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().setAll(NotificationBoard.current(this::showNotifications));
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
