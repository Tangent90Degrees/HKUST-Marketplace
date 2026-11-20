package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BuyerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnFavourites, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Hello, " + user.getFullName());
        }
        currentActiveBtn = btnBrowse;
        showBrowse();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        }
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showBrowse() {
        setActive(btnBrowse, "Browse Marketplace");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        category.setPromptText("Any category");
        ComboBox<ListingType> type = new ComboBox<>(FXCollections.observableArrayList(ListingType.values()));
        type.setPromptText("Any listing type");
        ComboBox<String> condition = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CONDITIONS));
        condition.setPromptText("Any condition");
        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList("Newest", "Price: low to high", "Price: high to low"));
        sort.getSelectionModel().selectFirst();
        TextField search = new TextField();
        search.setPromptText("Title or keyword");
        TextField low = new TextField();
        low.setPromptText("Min HK$");
        low.setPrefColumnCount(5);
        TextField high = new TextField();
        high.setPromptText("Max HK$");
        high.setPrefColumnCount(5);
        Button apply = new Button("Apply");
        Button reset = new Button("Reset");
        FlowPane filters = new FlowPane(8, 8, search, category, type, condition, low, high, sort, apply, reset);

        FlowPane grid = new FlowPane(16, 16);
        grid.setPrefWrapLength(900);
        Label feedback = new Label();
        Runnable refresh = () -> {
            try {
                var order = DummyDataService.CatalogOrder.values()[sort.getSelectionModel().getSelectedIndex()];
                var results = DummyDataService.getInstance().browse(search.getText(), category.getValue(), type.getValue(),
                        condition.getValue(), nullablePrice(low), nullablePrice(high), order);
                grid.getChildren().setAll(results.stream().map(this::createListingCard).toList());
                feedback.setText(results.isEmpty() ? "No matching live listings." : "");
            } catch (IllegalArgumentException | SecurityException failure) {
                feedback.setText(failure.getMessage());
            }
        };
        apply.setOnAction(event -> refresh.run());
        search.setOnAction(event -> refresh.run());
        reset.setOnAction(event -> {
            category.setValue(null);
            type.setValue(null);
            condition.setValue(null);
            search.clear();
            low.clear();
            high.clear();
            sort.getSelectionModel().selectFirst();
            refresh.run();
        });
        root.getChildren().addAll(filters, feedback, grid);
        refresh.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);
    }

    private VBox createListingCard(Listing listing) {
        VBox card = new VBox(8);
        card.getStyleClass().add("listing-card");
        card.setPrefWidth(260);
        card.setMinHeight(200);

        // Type badge + price
        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().add("badge");
        if (listing.getType() == ListingType.SELL) typeBadge.getStyleClass().add("badge-sold");
        else if (listing.getType() == ListingType.LEND) typeBadge.getStyleClass().add("badge-pending");
        else typeBadge.getStyleClass().add("badge-live");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label price = new Label(listing.getPriceDisplay());
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 16px;");
        top.getChildren().addAll(typeBadge, spacer, price);

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1a1a2e;");
        title.setWrapText(true);

        Label meta = new Label(listing.getCategory() + " · " + listing.getCondition());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label seller = new Label("Seller: " + listing.getSellerFullName());
        seller.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label location = new Label("📍 " + listing.getMeetupLocation());
        location.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label date = new Label("Posted: " + listing.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        date.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        Button details = new Button("View details");
        details.getStyleClass().addAll("btn-primary", "btn-sm");
        details.setOnAction(event -> showListingDetails(listing.getId()));
        if (!listing.getPhotos().isEmpty()) {
            addPhoto(card, listing.getPhotos().get(0), 230, 125);
        }
        card.getChildren().addAll(top, title, meta, seller, location, date,
                new Label(listing.getStatus().name()), details);
        return card;
    }

    private static Double nullablePrice(TextField field) {
        try {
            return field.getText().isBlank() ? null : Double.valueOf(field.getText());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Enter a numeric price.");
        }
    }

    private static void addPhoto(Pane target, byte[] photo, int width, int height) {
        Image image = new Image(new ByteArrayInputStream(photo), width, height, true, true);
        if (!image.isError()) {
            target.getChildren().add(new ImageView(image));
        }
    }

    private void showListingDetails(String listingId) {
        var service = DummyDataService.getInstance();
        try {
            Listing listing = service.openBuyerListing(listingId);
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle(listing.getTitle());
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            VBox body = new VBox(10);
            FlowPane photos = new FlowPane(8, 8);
            for (byte[] photo : listing.getPhotos()) {
                addPhoto(photos, photo, 200, 150);
            }
            var reviews = service.getReviewsForSeller(listing.getSellerUsername());
            String average = reviews.stream().mapToInt(review -> review.getRating()).average().stream()
                    .mapToObj(value -> String.format("%.1f / 5", value)).findFirst().orElse("No ratings");
            Label description = new Label(listing.getDescription());
            description.setWrapText(true);
            String terms = listing.getType() == ListingType.LEND
                    ? listing.getLendingSeconds() + " seconds" : listing.getPriceDisplay();
            body.getChildren().addAll(photos, description,
                    new Label(listing.getCategory() + " / " + listing.getCondition() + " / " + listing.getTypeDisplay()),
                    new Label(listing.getMeetupLocation() + " / Posted " + listing.getPostedAt().toLocalDate()),
                    new Label("Availability: " + listing.getStatus() + " / " + terms),
                    new Label(listing.getSellerFullName() + " / " + average));
            for (var review : reviews) {
                Label entry = new Label(review.getRating() + "/5 " + java.util.Objects.toString(review.getComment(), ""));
                entry.setWrapText(true);
                body.getChildren().add(entry);
            }
            Button sellerProfile = new Button("Seller profile");
            sellerProfile.setOnAction(event -> {
                String feedback = reviews.stream().map(review -> review.getRating() + "/5  "
                        + java.util.Objects.toString(review.getComment(), "")).collect(java.util.stream.Collectors.joining("\n"));
                Alert profile = new Alert(Alert.AlertType.INFORMATION, average + (feedback.isEmpty() ? "" : "\n\n" + feedback));
                profile.setHeaderText(service.sellerDisplayName(listing.getSellerUsername()));
                profile.showAndWait();
            });
            Button chat = new Button("Chat");
            chat.setOnAction(event -> showBuyerChat(listing));
            Button report = new Button("Report listing");
            report.setOnAction(event -> new TextInputDialog().showAndWait().ifPresent(reason ->
                    tryAction(() -> service.reportListing(listingId, reason), "Report received.")));
            HBox actions = new HBox(8, sellerProfile, chat, report);
            if (listing.getStatus() == ListingStatus.LIVE) {
                Button action = new Button(switch (listing.getType()) {
                    case SELL -> "Make offer";
                    case LEND -> "Request borrowing";
                    case GIVEAWAY -> "Claim item";
                });
                action.setOnAction(event -> makeProposal(listing, false));
                actions.getChildren().add(action);
            }
            body.getChildren().add(actions);
            ScrollPane scroll = new ScrollPane(body);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportWidth(630);
            scroll.setPrefViewportHeight(430);
            dialog.getDialogPane().setContent(scroll);
            dialog.showAndWait();
        } catch (IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    private void showBuyerChat(Listing listing) {
        var service = DummyDataService.getInstance();
        Dialog<Void> chat = new Dialog<>();
        chat.setTitle("Chat about " + listing.getTitle());
        chat.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextField search = new TextField();
        search.setPromptText("Search conversation");
        ListView<String> history = new ListView<>();
        history.setPrefSize(550, 260);
        history.setPlaceholder(new Label("No matching messages."));
        TextField compose = new TextField();
        compose.setPromptText("Write a message");
        Runnable reload = () -> history.getItems().setAll(service.buyerChat(listing.getId(), search.getText()).stream()
                .map(m -> m.sentAt() + "  @" + m.sender() + ": " + m.text()).toList());
        search.textProperty().addListener((property, before, after) -> reload.run());
        Button send = new Button("Send");
        send.setOnAction(event -> tryAction(() -> {
            service.sendBuyerMessage(listing.getId(), compose.getText());
            compose.clear();
            reload.run();
        }, null));
        HBox actions = new HBox(8, compose, send);
        if (listing.getType() != ListingType.GIVEAWAY && listing.getStatus() == ListingStatus.LIVE) {
            Button offer = new Button("Make formal offer");
            offer.setOnAction(event -> {
                makeProposal(listing, true);
                reload.run();
            });
            actions.getChildren().add(offer);
        }
        reload.run();
        chat.getDialogPane().setContent(new VBox(10, search, history, actions));
        chat.showAndWait();
    }

    private void makeProposal(Listing listing, boolean fromChat) {
        var service = DummyDataService.getInstance();
        switch (listing.getType()) {
            case SELL -> {
                TextInputDialog offer = new TextInputDialog(String.valueOf(listing.getPrice()));
                offer.setTitle("Make offer");
                offer.setHeaderText("Offer price in HK$");
                offer.showAndWait().ifPresent(price -> tryAction(() -> service.submitBuyerProposal(
                        listing.getId(), Double.valueOf(price.strip()), null, null, fromChat), "Offer sent."));
            }
            case LEND -> {
                Dialog<ButtonType> request = new Dialog<>();
                request.setTitle("Propose borrowing period");
                request.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                var now = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).withNano(0);
                TextField start = new TextField(now.toString());
                TextField end = new TextField(now.plusSeconds(listing.getLendingSeconds()).toString());
                request.getDialogPane().setContent(new VBox(8, new Label("Hong Kong time, e.g. 2026-11-22T16:00:00"),
                        new Label("Start"), start, new Label("End"), end));
                if (request.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                    tryAction(() -> service.submitBuyerProposal(listing.getId(), null,
                            LocalDateTime.parse(start.getText().strip()), LocalDateTime.parse(end.getText().strip()),
                            fromChat), "Borrowing request sent.");
                }
            }
            case GIVEAWAY -> tryAction(() -> service.submitBuyerProposal(listing.getId(), null, null, null, false),
                    "Claim recorded. The seller will review it.");
        }
    }

    private static void tryAction(Runnable operation, String success) {
        try {
            operation.run();
            if (success != null) {
                new Alert(Alert.AlertType.INFORMATION, success).showAndWait();
            }
        } catch (java.time.format.DateTimeParseException failure) {
            new Alert(Alert.AlertType.ERROR, "Use the date and time format shown.").showAndWait();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    @FXML
    private void showMyDeals() {
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        Label info = new Label("Active offers, borrow requests and claims");
        info.setStyle("-fx-text-fill: #5a5a7a;");
        root.getChildren().add(info);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<String[], String> colTitle = new TableColumn<>("Item");
        colTitle.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[0]));
        TableColumn<String[], String> colSeller = new TableColumn<>("Seller");
        colSeller.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[1]));
        TableColumn<String[], String> colType = new TableColumn<>("Type");
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[2]));
        TableColumn<String[], String> colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[3]));
        TableColumn<String[], String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[4]));

        table.getColumns().addAll(colTitle, colSeller, colType, colStatus, colAction);
        table.getColumns().forEach(column -> column.setSortable(false));

        var service = DummyDataService.getInstance();
        List<Deal> deals = service.buyerDeals();
        table.setItems(FXCollections.observableArrayList(deals.stream().map(deal -> new String[]{
                deal.getListingTitle(), service.sellerDisplayName(deal.getSellerUsername()), deal.displayType(),
                deal.getStatus(), deal.terms()
        }).toList()));
        Button details = new Button("Listing details");
        Button chat = new Button("Chat");
        Button complete = new Button("Complete or return");
        Button rate = new Button("Review seller");
        var selection = table.getSelectionModel();
        details.disableProperty().bind(selection.selectedItemProperty().isNull());
        chat.disableProperty().bind(selection.selectedItemProperty().isNull());
        complete.setDisable(true);
        rate.setDisable(true);
        selection.selectedIndexProperty().addListener((property, before, selected) -> {
            Deal deal = selected.intValue() < 0 ? null : deals.get(selected.intValue());
            complete.setDisable(deal == null || !"Accepted".equals(deal.getStatus()));
            rate.setDisable(deal == null || !deal.isCompleted());
        });
        details.setOnAction(event -> showListingDetails(deals.get(selection.getSelectedIndex()).getListingId()));
        chat.setOnAction(event -> {
            Listing listing = service.listingForCurrentActor(deals.get(selection.getSelectedIndex()).getListingId());
            if (listing != null) {
                tryAction(() -> showBuyerChat(listing), null);
            }
        });
        complete.setOnAction(event -> {
            Deal deal = deals.get(selection.getSelectedIndex());
            boolean loan = deal.getDealType() == ListingType.LEND;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, loan ? "Return this borrowing now?" : "Mark this deal complete?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                tryAction(() -> {
                    service.finishBuyerDeal(deal.getId());
                    if (!loan) {
                        rateDeal(deal);
                    }
                    showMyDeals();
                }, loan ? "Borrowing returned." : "Deal completed.");
            }
        });
        rate.setOnAction(event -> rateDeal(deals.get(selection.getSelectedIndex())));
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> showMyDeals());
        root.getChildren().addAll(table, new HBox(8, details, chat, complete, rate, refresh));

        contentPane.getChildren().add(root);
    }

    @FXML
    private void showHistory() {
        setActive(btnHistory, "Deal History");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(12);
        filters.setAlignment(Pos.CENTER_LEFT);
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        TextField search = new TextField();
        search.setPromptText("Search by title / seller...");
        filters.getChildren().addAll(new Label("Date range:"), from, new Label("–"), to, search);
        root.getChildren().add(filters);

        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<String[], String> c1 = new TableColumn<>("Item Title");
        c1.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[0]));
        TableColumn<String[], String> c2 = new TableColumn<>("Seller");
        c2.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[1]));
        TableColumn<String[], String> c3 = new TableColumn<>("Deal Date");
        c3.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[2]));
        TableColumn<String[], String> c4 = new TableColumn<>("Type");
        c4.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[3]));
        TableColumn<String[], String> c5 = new TableColumn<>("Outcome");
        c5.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue()[4]));
        table.getColumns().addAll(c1, c2, c3, c4, c5);
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        category.setPromptText("Any category");
        Button clearCategory = new Button("Clear category");
        clearCategory.setOnAction(event -> category.setValue(null));
        filters.getChildren().addAll(category, clearCategory);
        Label feedback = new Label();
        var service = DummyDataService.getInstance();
        Runnable apply = () -> {
            if (from.getValue() != null && to.getValue() != null && from.getValue().isAfter(to.getValue())) {
                feedback.setText("Start date must be on or before end date.");
                return;
            }
            String keyword = search.getText().strip().toLowerCase(java.util.Locale.ROOT);
            List<String[]> rows = service.buyerDeals().stream().filter(Deal::isCompleted)
                    .filter(deal -> deal.getCompletedAt() != null)
                    .filter(deal -> from.getValue() == null || !deal.getCompletedAt().toLocalDate().isBefore(from.getValue()))
                    .filter(deal -> to.getValue() == null || !deal.getCompletedAt().toLocalDate().isAfter(to.getValue()))
                    .filter(deal -> category.getValue() == null || (service.listingForCurrentActor(deal.getListingId()) != null
                            && category.getValue().equals(service.listingForCurrentActor(deal.getListingId()).getCategory())))
                    .filter(deal -> (deal.getListingTitle() + " " + deal.getSellerUsername() + " "
                            + service.sellerDisplayName(deal.getSellerUsername())).toLowerCase(java.util.Locale.ROOT).contains(keyword))
                    .map(deal -> new String[]{deal.getListingTitle(), service.sellerDisplayName(deal.getSellerUsername()),
                            deal.getCompletedAt().toLocalDate().toString(), deal.displayType(), deal.getStatus()}).toList();
            feedback.setText(rows.isEmpty() ? "No completed deals match." : "");
            table.getItems().setAll(rows);
        };
        for (var filter : List.<javafx.beans.value.ObservableValue<?>>of(
                from.valueProperty(), to.valueProperty(), category.valueProperty(), search.textProperty())) {
            filter.addListener((property, before, after) -> apply.run());
        }
        apply.run();
        root.getChildren().add(feedback);
        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    private void rateDeal(Deal deal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Review " + deal.getSellerUsername());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ComboBox<Integer> rating = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        rating.setPromptText("Select 1 to 5 stars");
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(rating.valueProperty().isNull());
        TextArea comment = new TextArea();
        comment.setPromptText("Write a review");
        comment.setPrefRowCount(4);
        dialog.getDialogPane().setContent(new VBox(8, rating, comment));
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            tryAction(() -> DummyDataService.getInstance().reviewCompletedDeal(
                    deal.getId(), rating.getValue(), comment.getText()), "Review submitted.");
        }
    }

    @FXML
    private void showFavourites() {
        setActive(btnFavourites, "Favourites & Following");
        contentPane.getChildren().clear();
        VBox root = new VBox(16);
        root.setPadding(new Insets(4));
        root.setAlignment(Pos.CENTER);
        Label icon = new Label("♥");
        icon.setStyle("-fx-font-size: 48px; -fx-text-fill: #A68137;");
        Label title = new Label("Favourites, Bookmarks & Following");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        Label desc = new Label("Save listings you like and follow favourite sellers.\nThis section is a UI placeholder for students to implement.");
        desc.setStyle("-fx-text-fill: #5a5a7a; -fx-text-alignment: center;");
        desc.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        root.getChildren().addAll(icon, title, desc);
        contentPane.getChildren().add(root);
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Notification> notifs;
        try {
            notifs = DummyDataService.getInstance().ownNotifications();
        } catch (SecurityException | IllegalStateException failure) {
            contentPane.getChildren().add(new Label(failure.getMessage()));
            return;
        }

        if (notifs.isEmpty()) {
            Label empty = new Label("No notifications yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (Notification n : notifs) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                Label title = new Label(n.getTitle());
                title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
                Label msg = new Label(n.getMessage());
                msg.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                msg.setWrapText(true);
                Label meta = new Label(n.getCategory() + " · " +
                        n.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(title, msg, meta);
                root.getChildren().add(item);
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "Manage Profile");
        contentPane.getChildren().clear();

        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(14);
        form.setPadding(new Insets(8));
        form.setMaxWidth(420);
        form.getStyleClass().add("card");

        Label title = new Label("Edit your profile");
        title.getStyleClass().add("section-title");

        TextField nameField = new TextField(user != null ? user.getFullName() : "");
        nameField.setPromptText("Full Name");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New password (leave blank to keep)");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new password");
        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(e -> {
            try {
                DummyDataService.getInstance().updateBuyerProfile(
                        nameField.getText(), newPass.getText(), confirmPass.getText());
                newPass.clear();
                confirmPass.clear();
                new Alert(Alert.AlertType.INFORMATION, "Profile updated successfully.").showAndWait();
            } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            }
        });

        form.getChildren().addAll(title,
                new Label("Full Name"), nameField,
                new Label("New Password"), newPass,
                new Label("Confirm Password"), confirmPass,
                saveBtn);

        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
