package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.NotificationBoard;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SellerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnCreate, btnStats, btnReviews, btnNotifications, btnProfile;

    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) welcomeLabel.setText("Hello, " + user.getFullName());
        currentActiveBtn = btnListings;
        showMyListings();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showMyListings() {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(14);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DummyDataService.getInstance()
                .getListingsBySeller(user != null ? user.getUsername() : "");

        if (myListings.isEmpty()) {
            Label empty = new Label("You have not posted any listings yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            root.getChildren().add(empty);
        } else {
            for (Listing l : myListings) {
                root.getChildren().add(createListingRow(l));
            }
        }
        root.getChildren().add(new Label("Incoming offers, requests, and claims"));
        List<Deal> proposals = DummyDataService.getInstance().sellerProposals();
        if (proposals.isEmpty()) {
            root.getChildren().add(new Label("No proposals yet."));
        }
        for (Deal deal : proposals) {
            VBox proposal = new VBox(6, new Label(deal.getListingTitle() + " / @" + deal.getBuyerUsername()),
                    new Label(deal.terms() + " / " + deal.getStatus()));
            proposal.getStyleClass().add("card");
            Button chat = new Button("Open chat");
            chat.setOnAction(event -> openSellerChat(deal.getListingId(), deal.getBuyerUsername()));
            HBox controls = new HBox(8, chat);
            if ("Pending".equals(deal.getStatus())) {
                for (var decision : DummyDataService.SellerDecision.values()) {
                    if (decision == DummyDataService.SellerDecision.COUNTER && deal.getDealType() == ListingType.GIVEAWAY) {
                        continue;
                    }
                    Button decide = new Button(decision.name());
                    decide.setOnAction(event -> decide(deal, decision, false));
                    controls.getChildren().add(decide);
                }
            }
            proposal.getChildren().add(controls);
            root.getChildren().add(proposal);
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createListingRow(Listing l) {
        HBox row = new HBox(16);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));

        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(l.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label meta = new Label(l.getCategory() + " · " + l.getTypeDisplay() + " · " + l.getMeetupLocation());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        info.getChildren().addAll(title, meta);

        Label status = new Label(l.getStatus().name().replace('_', ' '));
        status.getStyleClass().add("badge");
        switch (l.getStatus()) {
            case LIVE -> status.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> status.getStyleClass().add("badge-pending");
            case REJECTED -> status.getStyleClass().add("badge-rejected");
            default -> status.getStyleClass().add("badge-sold");
        }

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().addAll("btn-outline", "btn-sm");
        editBtn.setOnAction(e -> showListingForm(l));

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().addAll("btn-danger", "btn-sm");
        delBtn.setOnAction(e -> changeListing(l, null));
        editBtn.setDisable(l.getStatus() == ListingStatus.REMOVED);
        delBtn.setDisable(l.getStatus() == ListingStatus.REMOVED);

        Button chats = new Button("Chats");
        chats.setOnAction(event -> {
            List<String> buyers = DummyDataService.getInstance().sellerChatBuyers(l.getId());
            if (buyers.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "No conversations for this listing.").showAndWait();
            } else {
                ChoiceDialog<String> choice = new ChoiceDialog<>(buyers.get(0), buyers);
                choice.setTitle("Listing conversations");
                choice.showAndWait().ifPresent(buyer -> openSellerChat(l.getId(), buyer));
            }
        });

        row.getChildren().addAll(info, status, chats, editBtn, delBtn);
        return row;
    }

    private void changeListing(Listing original, Listing replacement) {
        var service = DummyDataService.getInstance();
        try {
            boolean restricted = service.sellerChangeRestricted(original.getId());
            String prompt = replacement == null ? "Delete this listing?" : "Save these listing changes?";
            if (restricted) {
                prompt += "\nThis listing is restricted. Affected buyers will be notified if you continue.";
            }
            if (new Alert(Alert.AlertType.CONFIRMATION, prompt).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                service.changeSellerListing(original.getId(), replacement, restricted);
                showMyListings();
            }
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    private void decide(Deal deal, DummyDataService.SellerDecision decision, boolean fromChat) {
        if (decision == DummyDataService.SellerDecision.COUNTER) {
            TextInputDialog prompt = new TextInputDialog(deal.getDealType() == ListingType.SELL
                    ? String.valueOf(deal.getOfferPrice()) : deal.getRequestedStart() + " / " + deal.getRequestedEnd());
            prompt.setTitle(fromChat ? "Send formal offer" : "Counter proposal");
            prompt.setHeaderText(deal.getDealType() == ListingType.SELL ? "Offer price in HK$"
                    : "Start / end in Hong Kong time (YYYY-MM-DDTHH:MM:SS)");
            prompt.showAndWait().ifPresent(terms -> {
                try {
                    if (deal.getDealType() == ListingType.SELL) {
                        saveDecision(deal, decision, Double.valueOf(terms.strip()), null, null, fromChat);
                    } else {
                        String[] period = terms.split("/", -1);
                        if (period.length != 2) {
                            throw new IllegalArgumentException("Enter both dates, separated by /.");
                        }
                        saveDecision(deal, decision, null, LocalDateTime.parse(period[0].strip()),
                                LocalDateTime.parse(period[1].strip()), fromChat);
                    }
                } catch (IllegalArgumentException | DateTimeParseException failure) {
                    new Alert(Alert.AlertType.ERROR, "Invalid counter terms. " + failure.getMessage()).showAndWait();
                }
            });
        } else {
            saveDecision(deal, decision, null, null, null, false);
        }
    }

    private void saveDecision(Deal deal, DummyDataService.SellerDecision decision, Double price,
                              LocalDateTime start, LocalDateTime end, boolean fromChat) {
        if (new Alert(Alert.AlertType.CONFIRMATION, "Confirm " + decision.name().toLowerCase(java.util.Locale.ROOT) + "?")
                .showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            var service = DummyDataService.getInstance();
            if (fromChat) {
                service.offerInSellerChat(deal.getListingId(), deal.getBuyerUsername(), price, start, end);
            } else {
                service.decideProposal(deal.getId(), decision, price, start, end, false);
            }
            new Alert(Alert.AlertType.INFORMATION, "Proposal updated.").showAndWait();
            showMyListings();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    private void openSellerChat(String listingId, String buyer) {
        var service = DummyDataService.getInstance();
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Chat with @" + buyer);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextField search = new TextField();
        search.setPromptText("Search conversation");
        TextField compose = new TextField();
        compose.setPromptText("Write a message");
        ListView<String> history = new ListView<>();
        history.setPrefSize(520, 250);
        Runnable reload = () -> history.getItems().setAll(service.sellerChat(listingId, buyer, search.getText()).stream()
                .map(message -> message.sentAt() + " @" + message.sender() + ": " + message.text()).toList());
        search.textProperty().addListener((property, oldValue, value) -> reload.run());
        Button send = new Button("Send");
        send.setOnAction(event -> {
            try {
                service.sendSellerMessage(listingId, buyer, compose.getText());
                compose.clear();
                reload.run();
            } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            }
        });
        HBox controls = new HBox(8, compose, send);
        Listing listing = service.findListing(listingId);
        if (listing.getStatus() == ListingStatus.LIVE && listing.getType() != ListingType.GIVEAWAY) {
            Deal terms = new Deal();
            terms.setListingId(listingId);
            terms.setBuyerUsername(buyer);
            terms.setDealType(listing.getType());
            terms.setOfferPrice(listing.getPrice());
            var now = LocalDateTime.now(java.time.ZoneId.of("Asia/Hong_Kong")).withNano(0);
            if (listing.getType() == ListingType.LEND) {
                terms.setRequestedPeriod(now, now.plusSeconds(listing.getLendingSeconds()));
            }
            Button offer = new Button("Send formal offer");
            offer.setOnAction(event -> {
                decide(terms, DummyDataService.SellerDecision.COUNTER, true);
                reload.run();
            });
            controls.getChildren().add(offer);
        }
        reload.run();
        dialog.getDialogPane().setContent(new VBox(10, search, history, controls));
        dialog.showAndWait();
    }

    @FXML
    private void showCreateListing() {
        showListingForm(null);
    }

    private void showListingForm(Listing original) {
        setActive(original == null ? btnCreate : btnListings, original == null ? "Create New Listing" : "Edit Listing");
        contentPane.getChildren().clear();

        VBox form = new VBox(12);
        form.setMaxWidth(520);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));

        Label title = new Label(original == null ? "Post a new item" : "Edit " + original.getTitle());
        title.getStyleClass().add("section-title");

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
        typeCombo.setPromptText("Listing type *");
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        TextField nameField = new TextField();
        nameField.setPromptText("Item name *");
        ComboBox<String> catCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        catCombo.setPromptText("Category *");
        catCombo.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> condCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CONDITIONS));
        condCombo.setPromptText("Condition *");
        condCombo.setMaxWidth(Double.MAX_VALUE);
        TextArea descArea = new TextArea();
        descArea.setPromptText("Description *");
        descArea.setPrefRowCount(3);

        TextField priceField = new TextField();
        priceField.setPromptText("Price (HK$) – required for Sell");
        TextField daysField = new TextField();
        daysField.setPromptText("Lending duration in seconds (minimum 5)");

        ComboBox<String> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        meetupCombo.setPromptText("Meet-up location *");
        meetupCombo.setMaxWidth(Double.MAX_VALUE);

        List<byte[]> photos = new ArrayList<>();
        Button choosePhotos = new Button("Choose photos");
        Label photoNote = new Label("Up to 6 photos");
        choosePhotos.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose listing photos");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif"));
            List<File> selected = chooser.showOpenMultipleDialog(form.getScene().getWindow());
            if (selected == null) {
                return;
            }
            try {
                if (selected.size() > 6) {
                    throw new IllegalArgumentException("Choose up to six photos.");
                }
                List<byte[]> nextPhotos = new ArrayList<>(selected.size());
                for (File file : selected) {
                    if (Files.size(file.toPath()) > 5 * 1024 * 1024) {
                        throw new IllegalArgumentException("Choose photos up to 5 MB each.");
                    }
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    if (new Image(new ByteArrayInputStream(bytes), 128, 128, true, true).isError()) {
                        throw new IllegalArgumentException("Cannot read photo: " + file.getName());
                    }
                    nextPhotos.add(bytes);
                }
                photos.clear();
                photos.addAll(nextPhotos);
                photoNote.setText(photos.size() + " photos selected");
            } catch (IllegalArgumentException | IOException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            }
        });

        Label priceLabel = new Label("Price (HK$)");
        Label durationLabel = new Label("Lending duration (seconds)");
        priceField.visibleProperty().bind(typeCombo.valueProperty().isEqualTo("Sell"));
        daysField.visibleProperty().bind(typeCombo.valueProperty().isEqualTo("Lend"));
        priceLabel.visibleProperty().bind(priceField.visibleProperty());
        durationLabel.visibleProperty().bind(daysField.visibleProperty());
        for (var field : List.of(priceField, daysField, priceLabel, durationLabel)) {
            field.managedProperty().bind(field.visibleProperty());
        }

        Button submitBtn = new Button("Submit for Review");
        if (original != null) {
            typeCombo.setValue(switch (original.getType()) {
                case SELL -> "Sell";
                case LEND -> "Lend";
                case GIVEAWAY -> "Give Away";
            });
            nameField.setText(original.getTitle());
            catCombo.setValue(original.getCategory());
            condCombo.setValue(original.getCondition());
            descArea.setText(original.getDescription());
            meetupCombo.setValue(original.getMeetupLocation());
            priceField.setText(java.util.Objects.toString(original.getPrice(), ""));
            daysField.setText(java.util.Objects.toString(original.getLendingSeconds(), ""));
            photos.addAll(original.getPhotos());
            photoNote.setText(photos.size() + " existing photos");
            submitBtn.setText("Save changes");
        }
        submitBtn.getStyleClass().add("btn-gold");
        submitBtn.setOnAction(e -> {
            if (typeCombo.getValue() == null || nameField.getText().isBlank() ||
                    catCombo.getValue() == null || condCombo.getValue() == null ||
                    meetupCombo.getValue() == null) {
                new Alert(Alert.AlertType.WARNING, "Please fill all required fields.").showAndWait();
                return;
            }
            Listing listing = new Listing();
            listing.setTitle(nameField.getText().trim());
            listing.setDescription(descArea.getText());
            listing.setCategory(catCombo.getValue());
            listing.setCondition(condCombo.getValue());
            listing.setMeetupLocation(meetupCombo.getValue());
            listing.setPhotos(photos);

            switch (typeCombo.getValue()) {
                case "Sell" -> {
                    listing.setType(ListingType.SELL);
                    try { listing.setPrice(Double.parseDouble(priceField.getText())); }
                    catch (Exception ex) {
                        new Alert(Alert.AlertType.WARNING, "Valid price required for Sell listings.").showAndWait();
                        return;
                    }
                }
                case "Lend" -> {
                    listing.setType(ListingType.LEND);
                    try { listing.setLendingSeconds(Long.parseLong(daysField.getText())); }
                    catch (NumberFormatException ex) {
                        new Alert(Alert.AlertType.WARNING, "Enter the lending duration in seconds.").showAndWait();
                        return;
                    }
                }
                default -> listing.setType(ListingType.GIVEAWAY);
            }
            if (original != null) {
                changeListing(original, listing);
                return;
            }
            if (new Alert(Alert.AlertType.CONFIRMATION, "Submit this listing for administrator review?")
                    .showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
            try {
                DummyDataService.getInstance().submitSellerListing(listing);
            } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
                return;
            }
            new Alert(Alert.AlertType.INFORMATION,
                    "Listing submitted. Status: Pending Review.").showAndWait();
            showMyListings();
        });

        form.getChildren().addAll(title,
                new Label("Listing Type *"), typeCombo,
                new Label("Item Name *"), nameField,
                new Label("Category *"), catCombo,
                new Label("Condition *"), condCombo,
                new Label("Description"), descArea,
                priceLabel, priceField,
                durationLabel, daysField,
                new Label("Meet-up Location *"), meetupCombo,
                choosePhotos, photoNote, submitBtn);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showStats() {
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();

        VBox root = new VBox(20);
        root.setPadding(new Insets(4));

        User user = SessionManager.getCurrentUser();
        List<Listing> myListings = DummyDataService.getInstance()
                .getListingsBySeller(user != null ? user.getUsername() : "");

        var service = DummyDataService.getInstance();
        var proposals = service.sellerProposals();
        int views = myListings.stream().mapToInt(Listing::getViewCount).sum();
        long completed = proposals.stream().filter(Deal::isCompleted).count();
        var average = service.getReviewsForSeller(user.getUsername()).stream().mapToInt(Review::getRating).average();
        HBox metrics = new HBox(16,
                metricCard("Views", Integer.toString(views), "#003974"),
                metricCard("Offers received", Long.toString(proposals.stream().filter(Deal::isBuyerSubmitted).count()), "#1565c0"),
                metricCard("Completed deals", Long.toString(completed), "#2e7d32"),
                metricCard("Average rating", average.isPresent() ? String.format("%.1f", average.getAsDouble()) : "No ratings", "#A68137"));
        root.getChildren().add(metrics);

        if (myListings.isEmpty()) {
            root.getChildren().add(new Label("No listing activity to chart."));
        } else {
            BarChart<String, Number> bars = new BarChart<>(new CategoryAxis(), new NumberAxis());
            bars.setTitle("Listing views by type");
            bars.setLegendVisible(false);
            bars.setPrefHeight(280);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            for (ListingType type : ListingType.values()) {
                int count = myListings.stream().filter(l -> l.getType() == type).mapToInt(Listing::getViewCount).sum();
                series.getData().add(new XYChart.Data<>(type.name(), count));
            }
            bars.getData().add(series);

            PieChart pie = new PieChart();
            pie.setTitle("Listings by status");
            pie.setPrefHeight(280);
            for (ListingStatus status : ListingStatus.values()) {
                long count = myListings.stream().filter(l -> l.getStatus() == status).count();
                if (count > 0) {
                    pie.getData().add(new PieChart.Data(status.name().replace('_', ' '), count));
                }
            }
            root.getChildren().add(new HBox(20, bars, pie));
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(140);
        card.setPadding(new Insets(16));
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(val, lbl);
        return card;
    }

    @FXML
    private void showReviews() {
        setActive(btnReviews, "Reviews & Feedback");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        var service = DummyDataService.getInstance();
        List<Review> reviews = service.sellerReviews();

        if (reviews.isEmpty()) {
            root.getChildren().add(new Label("No reviews yet."));
        } else {
            for (Review r : reviews) {
                VBox item = new VBox(6);
                item.getStyleClass().add("card");
                Label stars = new Label("★".repeat(r.getRating()) + "☆".repeat(5 - r.getRating()));
                stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
                Label comment = new Label(r.getComment());
                comment.setWrapText(true);
                Label meta = new Label("by @" + r.getBuyerUsername() + " · " + r.getListingTitle());
                meta.setText(meta.getText() + " / " + r.getCreatedAt().toLocalDate()
                        + (r.isHidden() ? " / hidden" : "") + (r.isFlagged() ? " / flagged" : ""));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                HBox actions = new HBox(8);
                Button reply = new Button("Reply");
                reply.getStyleClass().addAll("btn-outline", "btn-sm");
                reply.setDisable(r.isHidden());
                reply.setOnAction(event -> {
                    TextInputDialog prompt = new TextInputDialog();
                    prompt.setTitle("Reply to review");
                    prompt.showAndWait().ifPresent(value -> updateFeedback(() -> service.replyToReview(r.getId(), value)));
                });
                Button flag = new Button("Flag");
                flag.getStyleClass().addAll("btn-danger", "btn-sm");
                flag.setDisable(r.isHidden() || r.isFlagged());
                flag.setOnAction(event -> {
                    if (new Alert(Alert.AlertType.CONFIRMATION, "Flag this review for moderation?")
                            .showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        updateFeedback(() -> service.flagReview(r.getId()));
                    }
                });
                actions.getChildren().addAll(reply, flag);
                item.getChildren().addAll(stars, comment, meta);
                if (r.getSellerReply() != null) {
                    item.getChildren().add(new Label("Your reply: " + r.getSellerReply()));
                }
                item.getChildren().add(actions);
                root.getChildren().add(item);
            }
        }
        contentPane.getChildren().add(new ScrollPane(root));
    }

    private void updateFeedback(Runnable action) {
        try {
            action.run();
            new Alert(Alert.AlertType.INFORMATION, "Feedback updated.").showAndWait();
            showReviews();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
            new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
        }
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notifications");
        contentPane.getChildren().setAll(NotificationBoard.current(this::showNotifications));
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();
        User user = SessionManager.getCurrentUser();
        VBox form = new VBox(12);
        form.setMaxWidth(420);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(20));
        form.getChildren().add(new Label("Edit Seller Profile") {{ getStyleClass().add("section-title"); }});

        TextField name = new TextField(user != null ? user.getFullName() : "");
        PasswordField pass = new PasswordField();
        pass.setPromptText("New password (optional)");
        PasswordField confirmation = new PasswordField();
        TextArea bio = new TextArea(user != null && user.getBio() != null ? user.getBio() : "");
        bio.setPrefRowCount(3);
        ComboBox<String> meetup = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        if (user != null && user.getPreferredMeetup() != null) meetup.setValue(user.getPreferredMeetup());
        meetup.setMaxWidth(Double.MAX_VALUE);

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-gold");
        save.setOnAction(e -> {
            try {
                DummyDataService.getInstance().updateSellerProfile(
                        name.getText(), pass.getText(), confirmation.getText(), bio.getText(), meetup.getValue());
                pass.clear();
                confirmation.clear();
                new Alert(Alert.AlertType.INFORMATION, "Profile updated successfully.").showAndWait();
            } catch (IllegalArgumentException | IllegalStateException | SecurityException failure) {
                new Alert(Alert.AlertType.ERROR, failure.getMessage()).showAndWait();
            }
        });

        form.getChildren().addAll(
                new Label("Full Name"), name,
                new Label("New Password"), pass,
                new Label("Confirm Password"), confirmation,
                new Label("Bio"), bio,
                new Label("Preferred Meet-up"), meetup,
                save);
        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
