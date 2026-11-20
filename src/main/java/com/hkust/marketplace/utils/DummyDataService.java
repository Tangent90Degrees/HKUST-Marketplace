package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Shared marketplace service. The desktop entry point attaches the local snapshot store.
 */
public class DummyDataService {

    private static final String PASSWORD_HASH_PREFIX = "pbkdf2:";
    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private static final int PASSWORD_ITERATIONS = 120_000;

    private static final DummyDataService INSTANCE = new DummyDataService();

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final List<Deal> deals = new ArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<ListingReport> reports = new ArrayList<>();
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private MarketplaceStorage storage;

    private final AtomicInteger listingIdCounter = new AtomicInteger(100);
    private final AtomicInteger dealIdCounter = new AtomicInteger(500);
    private final AtomicInteger notifIdCounter = new AtomicInteger(1);
    private final AtomicInteger reviewIdCounter = new AtomicInteger(1);

    // Predefined HKUST locations
    public static final List<String> HKUST_LOCATIONS = List.of(
            "Academic Building (LG1)",
            "Library",
            "Student Centre",
            "Hall 1 Lobby",
            "Hall 2 Lobby",
            "Hall 3 Lobby",
            "Hall 4 Lobby",
            "Hall 5 Lobby",
            "Hall 6 Lobby",
            "Hall 7 Lobby",
            "Hall 8 Lobby",
            "Hall 9 Lobby",
            "UG Hall 1",
            "UG Hall 2",
            "North Gate",
            "South Gate",
            "Shaw Auditorium",
            "Coffee Shop (LG5)",
            "Sports Hall"
    );

    public static final List<String> CATEGORIES = List.of(
            "Textbooks", "Electronics", "Furniture", "Clothing",
            "Stationery", "Sports Equipment", "Kitchenware", "Others"
    );

    public static final List<String> CONDITIONS = List.of(
            "New", "Like New", "Used", "For Parts"
    );

    DummyDataService() {
        seedData();
    }

    public static DummyDataService getInstance() {
        return INSTANCE;
    }

    /** Opens the local data file, restoring existing records or persisting the initial demo data. */
    public synchronized void openLocalStorage(Path file) {
        MarketplaceStorage candidate = new MarketplaceStorage(file);
        try {
            MarketplaceStorage.Snapshot saved = candidate.load();
            if (saved == null) {
                candidate.save(snapshot());
            } else {
                users.clear();
                users.putAll(saved.users());
                listings.clear();
                listings.putAll(saved.listings());
                deals.clear();
                deals.addAll(saved.deals());
                notifications.clear();
                notifications.addAll(saved.notifications());
                reviews.clear();
                reviews.addAll(saved.reviews());
                messages.clear();
                reports.clear();
                if (saved.messages() != null) {
                    messages.addAll(saved.messages());
                }
                if (saved.reports() != null) {
                    reports.addAll(saved.reports());
                }
                listingIdCounter.set(saved.nextListing());
                dealIdCounter.set(saved.nextDeal());
                notifIdCounter.set(saved.nextNotification());
                reviewIdCounter.set(saved.nextReview());
            }
            storage = candidate;
        } catch (IOException e) {
            throw new IllegalStateException("Could not open the local marketplace data.", e);
        }
    }

    private MarketplaceStorage.Snapshot snapshot() {
        return new MarketplaceStorage.Snapshot(users, listings, deals, notifications, reviews, messages, reports,
                listingIdCounter.get(), dealIdCounter.get(), notifIdCounter.get(), reviewIdCounter.get());
    }

    private void saveChanges(Runnable undo) {
        if (storage == null) {
            return;
        }
        try {
            storage.save(snapshot());
        } catch (IOException failure) {
            undo.run();
            throw new IllegalStateException("Could not save the marketplace changes.", failure);
        }
    }

    public static String hashPassword(String password) {
        if (password == null || password.isBlank()) {
            return "";
        }
        byte[] salt = new byte[16];
        PASSWORD_RANDOM.nextBytes(salt);
        return PASSWORD_HASH_PREFIX + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derivePassword(password, salt));
    }

    /** Derives a 256-bit password hash from the supplied password and per-account salt. */
    private static byte[] derivePassword(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PASSWORD_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to hash password.", e);
        } finally {
            spec.clearPassword();
        }
    }

    public boolean verifyPassword(User user, String password) {
        if (user == null || password == null) {
            return false;
        }
        String stored = user.getPasswordHash();
        if (stored == null || !stored.startsWith(PASSWORD_HASH_PREFIX)) {
            return false;
        }
        String[] encoded = stored.substring(PASSWORD_HASH_PREFIX.length()).split(":", -1);
        if (encoded.length != 2) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(encoded[0]);
            byte[] hash = Base64.getDecoder().decode(encoded[1]);
            return salt.length == 16 && hash.length == 32
                    && MessageDigest.isEqual(hash, derivePassword(password, salt));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean verifyPassword(String username, String password) {
        User user = findUser(username);
        return verifyPassword(user, password);
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void seedData() {
        // Admin
        User admin = new User("admin", "System Admin", hashPassword("admin123"), UserRole.ADMIN);
        admin.setFullName("HKUST Marketplace Admin");
        users.put("admin", admin);

        // Sample buyers
        User buyer1 = new User("alice", "Alice Chan", hashPassword("password1"), UserRole.BUYER);
        buyer1.setResidenceHall("Hall 3");
        users.put("alice", buyer1);

        User buyer2 = new User("bob", "Bob Wong", hashPassword("password1"), UserRole.BUYER);
        buyer2.setResidenceHall("Off-campus");
        users.put("bob", buyer2);

        // Sample sellers
        User seller1 = new User("charlie", "Charlie Lee", hashPassword("password1"), UserRole.SELLER);
        seller1.setBio("Final year CSE student selling textbooks and gadgets.");
        seller1.setPreferredMeetup("Library");
        seller1.setAverageRating(4.7);
        seller1.setRatingCount(12);
        users.put("charlie", seller1);

        User seller2 = new User("diana", "Diana Ng", hashPassword("password1"), UserRole.SELLER);
        seller2.setBio("Selling furniture and kitchen items before graduation.");
        seller2.setPreferredMeetup("Hall 5 Lobby");
        seller2.setAverageRating(4.3);
        seller2.setRatingCount(8);
        users.put("diana", seller2);

        // Sample listings
        Listing l1 = createListing("Calculus Textbook (Stewart)", "Barely used, 8th edition. Perfect for MATH 1013.",
                "Textbooks", ListingType.SELL, "Like New", 180.0, null, "Library", "charlie", "Charlie Lee");
        l1.setStatus(ListingStatus.LIVE);
        listings.put(l1.getId(), l1);

        Listing l2 = createListing("MacBook Pro Charger 61W", "Original Apple charger, works perfectly.",
                "Electronics", ListingType.SELL, "Used", 250.0, null, "Academic Building (LG1)", "charlie", "Charlie Lee");
        l2.setStatus(ListingStatus.LIVE);
        listings.put(l2.getId(), l2);

        Listing l3 = createListing("IKEA Desk Lamp", "Good condition, LED bulb included.",
                "Furniture", ListingType.GIVEAWAY, "Used", null, null, "Hall 5 Lobby", "diana", "Diana Ng");
        l3.setStatus(ListingStatus.LIVE);
        listings.put(l3.getId(), l3);

        Listing l4 = createListing("Graphing Calculator TI-84", "Can lend for one semester.",
                "Electronics", ListingType.LEND, "Like New", null, 90, "Student Centre", "diana", "Diana Ng");
        l4.setStatus(ListingStatus.LIVE);
        listings.put(l4.getId(), l4);

        Listing l5 = createListing("Mini Fridge", "Works well, cleaning required.",
                "Furniture", ListingType.SELL, "Used", 400.0, null, "Hall 2 Lobby", "diana", "Diana Ng");
        l5.setStatus(ListingStatus.PENDING_REVIEW);
        listings.put(l5.getId(), l5);

        // Sample deals
        Deal d1 = new Deal();
        d1.setId("D" + dealIdCounter.getAndIncrement());
        d1.setListingId(l1.getId());
        d1.setListingTitle(l1.getTitle());
        d1.setBuyerUsername("alice");
        d1.setSellerUsername("charlie");
        d1.setDealType(ListingType.SELL);
        d1.setStatus("Accepted");
        d1.setOfferPrice(160.0);
        deals.add(d1);
        l1.setStatus(ListingStatus.RESERVED);

        // Sample notifications
        notifications.add(new Notification("alice", "Offer Accepted",
                "Charlie accepted your offer of HK$160 for Calculus Textbook.", "Offer"));
        notifications.add(new Notification("charlie", "New Offer Received",
                "Alice offered HK$160 for your Calculus Textbook.", "Offer"));
        notifications.add(new Notification("diana", "Listing Pending",
                "Your Mini Fridge listing is awaiting admin review.", "System"));
        notifications.add(new Notification("admin", "New Listing Submitted",
                "Diana submitted 'Mini Fridge' for review.", "System"));

        // Sample review
        Review r1 = new Review();
        r1.setId("R" + reviewIdCounter.getAndIncrement());
        r1.setSellerUsername("charlie");
        r1.setBuyerUsername("bob");
        r1.setListingTitle("Old Chemistry Book");
        r1.setRating(5);
        r1.setComment("Very smooth transaction, book in excellent condition!");
        reviews.add(r1);
    }

    private Listing createListing(String title, String desc, String cat, ListingType type,
                                  String condition, Double price, Integer days,
                                  String meetup, String sellerUser, String sellerName) {
        Listing l = new Listing();
        l.setId("L" + listingIdCounter.getAndIncrement());
        l.setTitle(title);
        l.setDescription(desc);
        l.setCategory(cat);
        l.setType(type);
        l.setCondition(condition);
        l.setPrice(price);
        l.setLendingDays(days);
        l.setMeetupLocation(meetup);
        l.setSellerUsername(sellerUser);
        l.setSellerFullName(sellerName);
        l.setPostedAt(LocalDateTime.now().minusDays(new Random().nextInt(10)));
        return l;
    }

    // ========== User methods ==========
    public boolean usernameExists(String username) {
        return users.containsKey(normalizeUsername(username));
    }

    public User findUser(String username) {
        return users.get(normalizeUsername(username));
    }

    public synchronized void registerUser(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("User username is required.");
        }
        String normalizedUsername = normalizeUsername(user.getUsername());
        if (users.containsKey(normalizedUsername)) {
            throw new IllegalArgumentException("Username already taken. Please choose another.");
        }
        String storedPassword = user.getPasswordHash();
        user.setPasswordHash(hashPassword(storedPassword));
        user.setUsername(normalizedUsername);
        users.put(normalizedUsername, user);
        saveChanges(() -> users.remove(normalizedUsername));
    }

    /** Creates a validated buyer account. Authentication remains a separate action. */
    public synchronized void registerBuyer(String username, String fullName, String password,
                                           String confirmation, String residence) {
        String name = requireFullName(fullName);
        requireNewPassword(password, confirmation);
        User buyer = new User(username, name, password, UserRole.BUYER);
        buyer.setResidenceHall(residence);
        registerUser(buyer);
    }

    /** Checks credentials, active status, and portal role before returning the authenticated user. */
    public synchronized User authenticate(String username, String password, UserRole portal) {
        if (normalizeUsername(username).isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Please enter both username and password.");
        }
        User user = findUser(username);
        if (!verifyPassword(user, password)) {
            throw new IllegalArgumentException("Username or password is incorrect.");
        }
        if (user.getRole() != portal) {
            throw new IllegalArgumentException("This account belongs to a different portal.");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException("This account has been deactivated.");
        }
        return user;
    }

    /** Validates and updates only the active buyer's own name and optional password. */
    public synchronized void updateBuyerProfile(String fullName, String password, String confirmation) {
        updateProfile(requireActor(UserRole.BUYER), fullName, password, confirmation, null, null);
    }

    /** Updates the signed-in administrator's name and optional password. */
    public synchronized void updateAdminProfile(String fullName, String password, String confirmation) {
        updateProfile(requireActor(UserRole.ADMIN), fullName, password, confirmation, null, null);
    }

    /** Registers and returns a validated seller account for the seller dashboard. */
    public synchronized User registerSeller(String username, String fullName, String password,
                                            String confirmation, String bio, String location) {
        validateLocation(location);
        String name = requireFullName(fullName);
        requireNewPassword(password, confirmation);
        User seller = new User(username, name, password, UserRole.SELLER);
        seller.setBio(bio == null ? "" : bio.strip());
        seller.setPreferredMeetup(location);
        registerUser(seller);
        return seller;
    }

    /** Updates the active seller's own profile and optional password. */
    public synchronized void updateSellerProfile(String fullName, String password, String confirmation,
                                                 String bio, String location) {
        User seller = requireActor(UserRole.SELLER);
        validateLocation(location);
        updateProfile(seller, fullName, password, confirmation, bio == null ? "" : bio.strip(), location);
    }

    private void updateProfile(User user, String fullName, String password, String confirmation,
                               String bio, String location) {
        String name = requireFullName(fullName);
        String priorHash = user.getPasswordHash();
        String priorName = user.getFullName();
        String priorBio = user.getBio();
        String priorLocation = user.getPreferredMeetup();
        if (!"".equals(password) || !"".equals(confirmation)) {
            requireNewPassword(password, confirmation);
            user.setPasswordHash(hashPassword(password));
        }
        user.setFullName(name);
        if (user.getRole() == UserRole.SELLER) {
            user.setBio(bio);
            user.setPreferredMeetup(location);
        }
        saveChanges(() -> {
            user.setFullName(priorName);
            user.setPasswordHash(priorHash);
            user.setBio(priorBio);
            user.setPreferredMeetup(priorLocation);
        });
    }

    private User requireActor(UserRole role) {
        User actor = SessionManager.getCurrentUser();
        if (actor == null || actor.getRole() != role || !actor.isActive()
                || findUser(actor.getUsername()) != actor) {
            throw new SecurityException("An active " + role.name().toLowerCase(Locale.ROOT)
                    + " session is required.");
        }
        return actor;
    }

    private static void validateLocation(String location) {
        if (location != null && !HKUST_LOCATIONS.contains(location)) {
            throw new IllegalArgumentException("Select a valid HKUST meet-up location.");
        }
    }

    private static String requireFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        return fullName.strip();
    }

    private static void requireNewPassword(String password, String confirmation) {
        String error = checkPasswordStrength(password);
        if (!"OK".equals(error)) {
            throw new IllegalArgumentException(error);
        }
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
    }

    public synchronized Collection<User> getAllUsers() {
        requireActor(UserRole.ADMIN);
        return users.values().stream().filter(user -> !user.isDeleted()).toList();
    }

    public List<User> getUsersByRole(UserRole role) {
        return users.values().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    // ========== Listing methods ==========
    public synchronized Collection<Listing> getAllListings() {
        requireActor(UserRole.ADMIN);
        return List.copyOf(listings.values());
    }

    public List<Listing> getLiveListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .collect(Collectors.toList());
    }

    public synchronized List<Listing> getPendingListings() {
        requireActor(UserRole.ADMIN);
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.PENDING_REVIEW)
                .collect(Collectors.toList());
    }

    public List<Listing> getListingsBySeller(String username) {
        return listings.values().stream()
                .filter(l -> l.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public Listing findListing(String id) {
        return listings.get(id);
    }

    public enum CatalogOrder { NEWEST, PRICE_LOW, PRICE_HIGH }

    /** Searches approved live listings. A price bound limits results to sale listings. */
    public synchronized List<Listing> browse(String keyword, String category, ListingType type, String condition,
                                            Double low, Double high, CatalogOrder order) {
        requireActor(UserRole.BUYER);
        if ((low != null && (!Double.isFinite(low) || low < 0))
                || (high != null && (!Double.isFinite(high) || high < 0))
                || (low != null && high != null && low > high)) {
            throw new IllegalArgumentException("Enter a valid price range.");
        }
        Comparator<Double> ascending = Comparator.naturalOrder();
        Comparator<Listing> sorting = switch (order) {
            case NEWEST -> Comparator.comparing(Listing::getPostedAt).reversed();
            case PRICE_LOW -> Comparator.comparing(Listing::getPrice, Comparator.nullsLast(ascending));
            case PRICE_HIGH -> Comparator.comparing(Listing::getPrice, Comparator.nullsLast(ascending.reversed()));
        };
        String query = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        return getLiveListings().stream()
                .filter(l -> (l.getTitle() + " " + l.getDescription()).toLowerCase(Locale.ROOT).contains(query))
                .filter(l -> category == null || category.equals(l.getCategory()))
                .filter(l -> condition == null || condition.equals(l.getCondition()))
                .filter(l -> type == null || type == l.getType())
                .filter(l -> low == null || (l.getPrice() != null && l.getPrice() >= low))
                .filter(l -> high == null || (l.getPrice() != null && l.getPrice() <= high))
                .sorted(sorting)
                .toList();
    }

    /** Opens a catalog or previously involved listing and counts the detail view. */
    public synchronized Listing openBuyerListing(String listingId) {
        User buyer = requireActor(UserRole.BUYER);
        Listing listing = buyerListing(listingId, buyer.getUsername());
        int before = listing.getViewCount();
        listing.setViewCount(before + 1);
        saveChanges(() -> listing.setViewCount(before));
        return listing;
    }

    private Listing buyerListing(String id, String buyer) {
        Listing listing = findListing(id);
        if (listing == null || (listing.getStatus() != ListingStatus.LIVE && deals.stream()
                .noneMatch(d -> id.equals(d.getListingId()) && buyer.equals(d.getBuyerUsername())))) {
            throw new IllegalStateException("This listing is unavailable.");
        }
        return listing;
    }

    /** Saves a buyer proposal and the associated notice and optional chat message atomically. */
    public synchronized Deal submitBuyerProposal(String listingId, Double price, LocalDateTime start,
                                                 LocalDateTime end, boolean fromChat) {
        User buyer = requireActor(UserRole.BUYER);
        Listing listing = buyerListing(listingId, buyer.getUsername());
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new IllegalStateException("This listing is no longer available.");
        }
        validateTerms(listing.getType(), price, start, end);
        if (listing.getType() == ListingType.GIVEAWAY && (fromChat || deals.stream().anyMatch(
                d -> listingId.equals(d.getListingId()) && "Pending".equals(d.getStatus())))) {
            throw new IllegalStateException("This giveaway cannot accept another claim.");
        }
        Deal deal = new Deal();
        deal.setId("D" + dealIdCounter.getAndIncrement());
        deal.setListingId(listingId);
        deal.setListingTitle(listing.getTitle());
        deal.setBuyerUsername(buyer.getUsername());
        deal.setSellerUsername(listing.getSellerUsername());
        deal.setDealType(listing.getType());
        deal.setOfferPrice(price);
        deal.setRequestedPeriod(start, end);
        deals.add(deal);
        Notification notice = createNotice(listing.getSellerUsername(), "New " + listing.getTypeDisplay() + " proposal",
                buyer.getFullName() + " proposed " + deal.terms() + " for " + listing.getTitle() + ".", "Offer");
        ChatMessage message = new ChatMessage(listingId, buyer.getUsername(), listing.getSellerUsername(),
                buyer.getUsername(), "Proposal: " + deal.terms(), deal.getId(), LocalDateTime.now(HONG_KONG));
        if (fromChat) {
            messages.add(message);
        }
        saveChanges(() -> {
            deals.remove(deal);
            notifications.remove(notice);
            if (fromChat) {
                messages.remove(message);
            }
        });
        return deal;
    }

    private static void validateTerms(ListingType type, Double price, LocalDateTime start, LocalDateTime end) {
        if (type == ListingType.SELL && (price == null || !Double.isFinite(price) || price < 0)) {
            throw new IllegalArgumentException("Enter a valid nonnegative offer price.");
        }
        if (type == ListingType.LEND && (start == null || end == null || Duration.between(start, end).getSeconds() < 5)) {
            throw new IllegalArgumentException("Borrowing must last at least five seconds.");
        }
    }

    private Notification createNotice(String recipient, String title, String message, String category) {
        Notification notice = new Notification(recipient, title, message, category);
        notice.setId("N" + notifIdCounter.getAndIncrement());
        notifications.add(notice);
        return notice;
    }

    /** Searches the current buyer's retained thread for one listing. */
    public synchronized List<ChatMessage> buyerChat(String listingId, String query) {
        User buyer = requireActor(UserRole.BUYER);
        Listing listing = buyerListing(listingId, buyer.getUsername());
        return searchChat(listingId, buyer.getUsername(), listing.getSellerUsername(), query);
    }

    private List<ChatMessage> searchChat(String listingId, String buyer, String seller, String query) {
        String keyword = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return messages.stream().filter(m -> m.listingId().equals(listingId) && m.buyer().equals(buyer)
                        && m.seller().equals(seller) && m.text().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
    }

    /** Adds a text message to the buyer's private thread. */
    public synchronized void sendBuyerMessage(String listingId, String text) {
        User buyer = requireActor(UserRole.BUYER);
        Listing listing = buyerListing(listingId, buyer.getUsername());
        addTextMessage(listing, buyer.getUsername(), buyer.getUsername(), listing.getSellerUsername(), text);
    }

    /** Returns the active seller's proposals for their own listings. */
    public synchronized List<Deal> sellerProposals() {
        User seller = requireActor(UserRole.SELLER);
        return deals.stream().filter(d -> seller.getUsername().equals(d.getSellerUsername())).toList();
    }

    public enum SellerDecision { ACCEPT, DECLINE, COUNTER }

    /** Confirms a pending proposal and records the state and notice together. */
    public synchronized void decideProposal(String id, SellerDecision decision, Double price,
                                              LocalDateTime start, LocalDateTime end, boolean fromChat) {
        User seller = requireActor(UserRole.SELLER);
        Deal proposal = sellerProposals().stream().filter(d -> id.equals(d.getId())).findFirst()
                .orElseThrow(() -> new SecurityException("This proposal belongs to another seller."));
        Listing listing = sellerListing(proposal.getListingId(), seller);
        if (!"Pending".equals(proposal.getStatus())
                || (decision != SellerDecision.DECLINE && listing.getStatus() != ListingStatus.LIVE)) {
            throw new IllegalStateException("This proposal or listing is no longer available.");
        }
        if (decision == SellerDecision.COUNTER) {
            if (proposal.getDealType() == ListingType.GIVEAWAY) {
                throw new IllegalArgumentException("Giveaway claims cannot be countered.");
            }
            validateTerms(proposal.getDealType(), price, start, end);
        }
        Double previousPrice = proposal.getOfferPrice();
        LocalDateTime previousStart = proposal.getRequestedStart();
        LocalDateTime previousEnd = proposal.getRequestedEnd();
        ListingStatus previousListingStatus = listing.getStatus();
        Map<Deal, String> closed = new LinkedHashMap<>();
        switch (decision) {
            case ACCEPT -> {
                proposal.setStatus("Accepted");
                listing.setStatus(proposal.getDealType() == ListingType.LEND && !previousStart.isAfter(LocalDateTime.now(HONG_KONG))
                        ? ListingStatus.LENT : ListingStatus.RESERVED);
                for (Deal other : deals) {
                    if (other != proposal && other.getListingId().equals(listing.getId()) && isOpenProposal(other)) {
                        closed.put(other, other.getStatus());
                        other.setStatus("Cancelled");
                    }
                }
            }
            case DECLINE -> proposal.setStatus("Declined");
            case COUNTER -> {
                proposal.setStatus("Countered");
                proposal.setOfferPrice(price);
                proposal.setRequestedPeriod(start, end);
            }
        }
        Notification notice = createNotice(proposal.getBuyerUsername(), "Proposal " + proposal.getStatus(),
                listing.getTitle() + ": " + proposal.terms(), "Offer");
        ChatMessage formal = new ChatMessage(listing.getId(), proposal.getBuyerUsername(), seller.getUsername(),
                seller.getUsername(), "Counter: " + proposal.terms(), proposal.getId(), LocalDateTime.now(HONG_KONG));
        if (fromChat && decision == SellerDecision.COUNTER) {
            messages.add(formal);
        }
        saveChanges(() -> {
            proposal.setStatus("Pending");
            proposal.setOfferPrice(previousPrice);
            proposal.setRequestedPeriod(previousStart, previousEnd);
            listing.setStatus(previousListingStatus);
            closed.forEach(Deal::setStatus);
            notifications.remove(notice);
            messages.remove(formal);
        });
    }

    private static boolean isOpenProposal(Deal deal) {
        return "Pending".equals(deal.getStatus()) || "Countered".equals(deal.getStatus());
    }

    private Listing sellerListing(String id, User seller) {
        Listing listing = findListing(id);
        if (listing == null || !seller.getUsername().equals(listing.getSellerUsername())) {
            throw new SecurityException("This listing belongs to another seller.");
        }
        return listing;
    }

    /** Lists existing buyers in this seller's listing conversation. */
    public synchronized List<String> sellerChatBuyers(String listingId) {
        User seller = requireActor(UserRole.SELLER);
        sellerListing(listingId, seller);
        Set<String> buyers = new LinkedHashSet<>();
        deals.stream().filter(d -> listingId.equals(d.getListingId())).map(Deal::getBuyerUsername).forEach(buyers::add);
        messages.stream().filter(m -> listingId.equals(m.listingId())).map(ChatMessage::buyer).forEach(buyers::add);
        return List.copyOf(buyers);
    }

    public synchronized List<ChatMessage> sellerChat(String listingId, String buyer, String query) {
        User seller = requireActor(UserRole.SELLER);
        if (!sellerChatBuyers(listingId).contains(buyer)) {
            throw new SecurityException("That conversation is unavailable.");
        }
        return searchChat(listingId, buyer, seller.getUsername(), query);
    }

    public synchronized void sendSellerMessage(String listingId, String buyer, String text) {
        User seller = requireActor(UserRole.SELLER);
        if (!sellerChatBuyers(listingId).contains(buyer)) {
            throw new SecurityException("That conversation is unavailable.");
        }
        addTextMessage(sellerListing(listingId, seller), buyer, seller.getUsername(), buyer, text);
    }

    /** Offers sale or lending terms in an existing conversation without requiring a pending buyer offer. */
    public synchronized void offerInSellerChat(String listingId, String buyer, Double price,
                                               LocalDateTime start, LocalDateTime end) {
        User seller = requireActor(UserRole.SELLER);
        Listing listing = sellerListing(listingId, seller);
        if (!sellerChatBuyers(listingId).contains(buyer) || listing.getStatus() != ListingStatus.LIVE
                || listing.getType() == ListingType.GIVEAWAY) {
            throw new IllegalStateException("Formal terms are unavailable for this conversation.");
        }
        validateTerms(listing.getType(), price, start, end);
        var pending = sellerProposals().stream().filter(d -> listingId.equals(d.getListingId())
                && buyer.equals(d.getBuyerUsername()) && "Pending".equals(d.getStatus())).findFirst();
        if (pending.isPresent()) {
            decideProposal(pending.get().getId(), SellerDecision.COUNTER, price, start, end, true);
            return;
        }
        Deal proposal = new Deal();
        proposal.setId("D" + dealIdCounter.getAndIncrement());
        proposal.setListingId(listingId);
        proposal.setListingTitle(listing.getTitle());
        proposal.setBuyerUsername(buyer);
        proposal.setSellerUsername(seller.getUsername());
        proposal.setDealType(listing.getType());
        proposal.setStatus("Countered");
        proposal.setOfferPrice(price);
        proposal.setRequestedPeriod(start, end);
        deals.add(proposal);
        ChatMessage message = new ChatMessage(listingId, buyer, seller.getUsername(), seller.getUsername(),
                "Seller offer: " + proposal.terms(), proposal.getId(), LocalDateTime.now(HONG_KONG));
        messages.add(message);
        Notification notice = createNotice(buyer, "Seller offered terms", listing.getTitle() + ": " + proposal.terms(), "Offer");
        saveChanges(() -> {
            deals.remove(proposal);
            messages.remove(message);
            notifications.remove(notice);
        });
    }

    public synchronized boolean sellerChangeRestricted(String id) {
        Listing listing = sellerListing(id, requireActor(UserRole.SELLER));
        return switch (listing.getStatus()) {
            case RESERVED, SOLD, LENT, GIVEN -> true;
            default -> deals.stream().anyMatch(d -> id.equals(d.getListingId()) && isOpenProposal(d));
        };
    }

    /** Applies a confirmed seller edit, or removes the listing when edited is null. */
    public synchronized void changeSellerListing(String id, Listing edited, boolean restrictedConfirmed) {
        User seller = requireActor(UserRole.SELLER);
        Listing original = sellerListing(id, seller);
        if (original.getStatus() == ListingStatus.REMOVED) {
            throw new IllegalStateException("This listing was already removed.");
        }
        if (sellerChangeRestricted(id) && !restrictedConfirmed) {
            throw new IllegalStateException("Review the restriction warning before confirming the change.");
        }
        ListingStatus priorStatus = original.getStatus();
        if (edited != null) {
            validateListing(edited);
            retainListingIdentity(original, edited);
            listings.put(id, edited);
        } else {
            original.setStatus(ListingStatus.REMOVED);
        }
        List<Notification> sent = new ArrayList<>();
        deals.stream().filter(d -> id.equals(d.getListingId()) && (isOpenProposal(d) || "Accepted".equals(d.getStatus())))
                .map(Deal::getBuyerUsername).distinct().forEach(buyer -> sent.add(createNotice(buyer,
                        "Seller changed a listing", original.getTitle() + (edited == null ? " was removed." : " was updated."),
                        "Listing")));
        saveChanges(() -> {
            listings.put(id, original);
            if (edited == null) {
                original.setStatus(priorStatus);
            }
            notifications.removeAll(sent);
        });
    }

    private static void retainListingIdentity(Listing original, Listing edited) {
        edited.setId(original.getId());
        edited.setSellerUsername(original.getSellerUsername());
        edited.setSellerFullName(original.getSellerFullName());
        edited.setStatus(original.getStatus());
        edited.setPostedAt(original.getPostedAt());
        edited.setReviewedAt(original.getReviewedAt());
        edited.setRejectionReason(original.getRejectionReason());
        edited.setViewCount(original.getViewCount());
        edited.setRemovalReason(original.getRemovalReason());
        edited.setCreatedByAdmin(original.getCreatedByAdmin());
    }

    /** Publishes a validated listing on behalf of an active seller and records who created it. */
    public synchronized void adminCreateListing(String sellerName, Listing draft) {
        User admin = requireActor(UserRole.ADMIN);
        User seller = moderatedUser(sellerName);
        if (seller.getRole() != UserRole.SELLER || !seller.isActive() || draft.getId() != null) {
            throw new IllegalArgumentException("Select an active seller and provide a new listing.");
        }
        validateListing(draft);
        draft.setId("L" + listingIdCounter.getAndIncrement());
        draft.setSellerUsername(seller.getUsername());
        draft.setSellerFullName(seller.getFullName());
        draft.setCreatedByAdmin(admin.getUsername());
        draft.setStatus(ListingStatus.LIVE);
        listings.put(draft.getId(), draft);
        Notification notice = createNotice(seller.getUsername(), "Listing published by administrator", draft.getTitle(), "Listing");
        saveChanges(() -> {
            listings.remove(draft.getId());
            notifications.remove(notice);
            draft.setId(null);
        });
    }

    /** Edits listing details and closes every unaccepted proposal. Accepted deals retain their terms. */
    public synchronized void adminEditListing(String id, Listing edited) {
        requireActor(UserRole.ADMIN);
        Listing original = requireListing(id);
        validateListing(edited);
        retainListingIdentity(original, edited);
        Map<Deal, String> closed = new LinkedHashMap<>();
        List<Notification> sent = new ArrayList<>();
        closeProposals(deal -> id.equals(deal.getListingId()), closed);
        notifyAffectedBuyers(closed.keySet(), original, "A listing you proposed on was edited.", sent);
        listings.put(id, edited);
        sent.add(createNotice("admin", "Listing Edited", original.getTitle(), "Audit"));
        saveChanges(() -> {
            listings.put(id, original);
            closed.forEach(Deal::setStatus);
            notifications.removeAll(sent);
        });
    }

    /** Removes a live listing and sends the reason only to buyers who had open proposals on it. */
    public synchronized void adminTakeDown(String id, String reason) {
        requireActor(UserRole.ADMIN);
        Listing listing = requireListing(id);
        if (listing.getStatus() != ListingStatus.LIVE || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A live listing and a written reason are required.");
        }
        List<Notification> sent = new ArrayList<>();
        Map<Deal, String> closed = new LinkedHashMap<>();
        closeProposals(deal -> id.equals(deal.getListingId()), closed);
        notifyAffectedBuyers(closed.keySet(), listing, reason.strip(), sent);
        sent.add(createNotice("admin", "Listing Taken Down", listing.getTitle() + ": " + reason.strip(), "Audit"));
        String oldReason = listing.getRemovalReason();
        listing.setRemovalReason(reason.strip());
        listing.setStatus(ListingStatus.REMOVED);
        saveChanges(() -> {
            listing.setStatus(ListingStatus.LIVE);
            listing.setRemovalReason(oldReason);
            closed.forEach(Deal::setStatus);
            notifications.removeAll(sent);
        });
    }

    private Listing requireListing(String id) {
        Listing listing = findListing(id);
        if (listing == null) {
            throw new IllegalStateException("This listing is unavailable.");
        }
        return listing;
    }

    private void closeProposals(java.util.function.Predicate<Deal> affected, Map<Deal, String> closed) {
        deals.stream().filter(affected).filter(DummyDataService::isOpenProposal).forEach(deal -> {
            closed.put(deal, deal.getStatus());
            deal.setStatus("Cancelled");
        });
    }

    private void notifyAffectedBuyers(Collection<Deal> affected, Listing listing, String reason, List<Notification> sent) {
        affected.stream().map(Deal::getBuyerUsername).distinct().forEach(buyer -> sent.add(
                createNotice(buyer, "Listing updated", listing.getTitle() + ": " + reason, "Listing")));
    }

    /** Disables the account and closes unaccepted work. Permanent removal prevents reactivation. */
    public synchronized void moderateAccount(String username, boolean delete) {
        requireActor(UserRole.ADMIN);
        User user = moderatedUser(username);
        String key = user.getUsername();
        if (deals.stream().anyMatch(deal -> involves(deal, key) && "Accepted".equals(deal.getStatus()))) {
            throw new IllegalStateException("This account has a reserved or in-progress deal.");
        }
        if (!user.isActive() && !delete) {
            throw new IllegalStateException("This account is already deactivated.");
        }
        Map<Deal, String> closed = new LinkedHashMap<>();
        Map<Listing, ListingStatus> removed = new LinkedHashMap<>();
        Map<Listing, String> reasons = new LinkedHashMap<>();
        List<Notification> sent = new ArrayList<>();
        String action = delete ? "Account permanently removed" : "Account deactivated";
        if (user.getRole() == UserRole.SELLER) {
            for (Listing listing : getListingsBySeller(key)) {
                if (listing.getStatus() == ListingStatus.LIVE) {
                    var affected = deals.stream().filter(deal -> listing.getId().equals(deal.getListingId()))
                            .filter(DummyDataService::isOpenProposal).toList();
                    notifyAffectedBuyers(affected, listing, action, sent);
                }
                if (listing.getStatus() == ListingStatus.LIVE || listing.getStatus() == ListingStatus.PENDING_REVIEW) {
                    removed.put(listing, listing.getStatus());
                    reasons.put(listing, listing.getRemovalReason());
                    listing.setStatus(ListingStatus.REMOVED);
                    listing.setRemovalReason(action);
                }
            }
        }
        closeProposals(deal -> involves(deal, key), closed);
        boolean wasActive = user.isActive();
        user.setActive(false);
        user.setDeleted(delete);
        sent.add(createNotice("admin", action, "@" + key, "Audit"));
        saveChanges(() -> {
            user.setActive(wasActive);
            user.setDeleted(false);
            removed.forEach(Listing::setStatus);
            reasons.forEach(Listing::setRemovalReason);
            closed.forEach(Deal::setStatus);
            notifications.removeAll(sent);
        });
    }

    private static boolean involves(Deal deal, String username) {
        return username.equals(deal.getBuyerUsername()) || username.equals(deal.getSellerUsername());
    }

    private User moderatedUser(String username) {
        User user = findUser(username);
        if (user == null || user.getRole() == UserRole.ADMIN || user.isDeleted()) {
            throw new IllegalStateException("This buyer or seller account is unavailable.");
        }
        return user;
    }

    /** Renames account references together with the edited profile so history and chat stay linked. */
    public synchronized void adminEditUser(String current, String username, String fullName,
                                           String residence, String bio, String location) {
        requireActor(UserRole.ADMIN);
        User user = moderatedUser(current);
        String old = user.getUsername();
        String next = normalizeUsername(username);
        String name = requireFullName(fullName);
        if (next.isEmpty() || (!next.equals(old) && users.containsKey(next))) {
            throw new IllegalArgumentException("Enter an available username.");
        }
        if (user.getRole() == UserRole.SELLER) {
            validateLocation(location);
        }
        String priorName = user.getFullName();
        String priorResidence = user.getResidenceHall();
        String priorBio = user.getBio();
        String priorLocation = user.getPreferredMeetup();
        renameUser(user, old, next, name);
        if (user.getRole() == UserRole.SELLER) {
            user.setBio(bio);
            user.setPreferredMeetup(location);
        } else {
            user.setResidenceHall(residence);
        }
        Notification notice = createNotice("admin", "Account Edited", "@" + old + " → @" + next, "Audit");
        saveChanges(() -> {
            notifications.remove(notice);
            renameUser(user, next, old, priorName);
            user.setResidenceHall(priorResidence);
            user.setBio(priorBio);
            user.setPreferredMeetup(priorLocation);
        });
    }

    private void renameUser(User user, String before, String after, String name) {
        users.remove(before);
        user.setUsername(after);
        user.setFullName(name);
        users.put(after, user);
        listings.values().stream().filter(l -> before.equals(l.getSellerUsername())).forEach(l -> {
            l.setSellerUsername(after);
            l.setSellerFullName(name);
        });
        deals.forEach(d -> {
            d.setBuyerUsername(renamed(d.getBuyerUsername(), before, after));
            d.setSellerUsername(renamed(d.getSellerUsername(), before, after));
        });
        reviews.forEach(r -> {
            r.setBuyerUsername(renamed(r.getBuyerUsername(), before, after));
            r.setSellerUsername(renamed(r.getSellerUsername(), before, after));
        });
        notifications.forEach(n -> n.setUsername(renamed(n.getUsername(), before, after)));
        messages.replaceAll(m -> new ChatMessage(m.listingId(), renamed(m.buyer(), before, after),
                renamed(m.seller(), before, after), renamed(m.sender(), before, after), m.text(), m.proposalId(), m.sentAt()));
        reports.replaceAll(r -> new ListingReport(r.listingId(), renamed(r.buyer(), before, after), r.reason(), r.filedAt()));
    }

    private static String renamed(String value, String before, String after) {
        return before.equals(value) ? after : value;
    }

    private void addTextMessage(Listing listing, String buyer, String sender, String recipient, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter a message.");
        }
        ChatMessage message = new ChatMessage(listing.getId(), buyer, listing.getSellerUsername(), sender,
                text.strip(), null, LocalDateTime.now(HONG_KONG));
        messages.add(message);
        Notification notice = createNotice(recipient, "New chat message", "New message about " + listing.getTitle(), "Chat");
        saveChanges(() -> {
            messages.remove(message);
            notifications.remove(notice);
        });
    }

    /** Saves a report and alerts the administrator. The listing state is unaffected. */
    public synchronized void reportListing(String listingId, String reason) {
        User buyer = requireActor(UserRole.BUYER);
        Listing listing = buyerListing(listingId, buyer.getUsername());
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter the report details.");
        }
        ListingReport report = new ListingReport(listingId, buyer.getUsername(), reason.strip(), LocalDateTime.now(HONG_KONG));
        reports.add(report);
        Notification notice = createNotice("admin", "Listing Reported", listing.getTitle() + ": " + report.reason(), "Report");
        saveChanges(() -> {
            reports.remove(report);
            notifications.remove(notice);
        });
    }

    /** Records a pending listing decision and its seller notification as one write. */
    public synchronized void reviewListing(String listingId, boolean approve, String reason) {
        requireActor(UserRole.ADMIN);
        Listing listing = findListing(listingId);
        if (listing == null || listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new IllegalStateException("This listing is no longer pending review.");
        }
        User seller = findUser(listing.getSellerUsername());
        if (approve && (seller == null || !seller.isActive() || seller.getRole() != UserRole.SELLER)) {
            throw new IllegalStateException("Only listings from active sellers can be approved.");
        }
        if (!approve && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A written rejection reason is required.");
        }
        LocalDateTime priorReviewedAt = listing.getReviewedAt();
        String priorReason = listing.getRejectionReason();
        listing.setStatus(approve ? ListingStatus.LIVE : ListingStatus.REJECTED);
        listing.setReviewedAt(LocalDateTime.now());
        listing.setRejectionReason(approve ? null : reason.strip());
        String title = approve ? "Listing Approved" : "Listing Rejected";
        String message = approve ? "Your listing '" + listing.getTitle() + "' is now live."
                : "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + reason.strip();
        Notification notice = new Notification(listing.getSellerUsername(), title, message, "Listing");
        notice.setId("N" + notifIdCounter.getAndIncrement());
        notifications.add(notice);
        saveChanges(() -> {
            listing.setStatus(ListingStatus.PENDING_REVIEW);
            listing.setReviewedAt(priorReviewedAt);
            listing.setRejectionReason(priorReason);
            notifications.remove(notice);
        });
    }

    /** Saves a pending seller listing and its administrator notification together. */
    public synchronized void submitSellerListing(Listing draft) {
        User seller = requireActor(UserRole.SELLER);
        validateListing(draft);
        if (draft.getId() != null) {
            throw new IllegalArgumentException("Submit a new listing.");
        }
        draft.setSellerUsername(seller.getUsername());
        draft.setSellerFullName(seller.getFullName());
        draft.setStatus(ListingStatus.PENDING_REVIEW);
        draft.setId("L" + listingIdCounter.getAndIncrement());
        listings.put(draft.getId(), draft);
        Notification notice = new Notification("admin", "New Listing Submitted",
                seller.getFullName() + " submitted '" + draft.getTitle() + "' for review.", "Listing");
        notice.setId("N" + notifIdCounter.getAndIncrement());
        notifications.add(notice);
        saveChanges(() -> {
            notifications.remove(notice);
            listings.remove(draft.getId());
            draft.setId(null);
        });
    }

    private static void validateListing(Listing draft) {
        if (draft.getType() == null || draft.getTitle() == null || draft.getTitle().isBlank()
                || draft.getDescription() == null || draft.getDescription().isBlank()
                || !CATEGORIES.contains(draft.getCategory() == null ? "" : draft.getCategory())
                || !CONDITIONS.contains(draft.getCondition() == null ? "" : draft.getCondition())
                || !HKUST_LOCATIONS.contains(draft.getMeetupLocation() == null ? "" : draft.getMeetupLocation())) {
            throw new IllegalArgumentException("Fill in all required listing fields.");
        }
        if (draft.getType() == ListingType.SELL && (draft.getPrice() == null
                || !Double.isFinite(draft.getPrice()) || draft.getPrice() <= 0)) {
            throw new IllegalArgumentException("The sale price must be positive.");
        }
        if (draft.getType() == ListingType.LEND && (draft.getLendingSeconds() == null
                || draft.getLendingSeconds() < 5)) {
            throw new IllegalArgumentException("The lending duration must be at least five seconds.");
        }
        List<byte[]> photos = draft.getPhotos();
        if (photos.size() > 6 || photos.stream().anyMatch(p -> p.length == 0 || p.length > 5 * 1024 * 1024)) {
            throw new IllegalArgumentException("Attach up to six photos, up to 5 MB each.");
        }
    }

    public void addListing(Listing listing) {
        if (listing.getId() == null) {
            listing.setId("L" + listingIdCounter.getAndIncrement());
        }
        listings.put(listing.getId(), listing);
    }

    public void updateListing(Listing listing) {
        listings.put(listing.getId(), listing);
    }

    public void removeListing(String id) {
        listings.remove(id);
    }

    // ========== Deal methods ==========
    public List<Deal> getDealsForBuyer(String username) {
        return deals.stream()
                .filter(d -> d.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Deal> getAllDeals() {
        return new ArrayList<>(deals);
    }

    public void addDeal(Deal deal) {
        if (deal.getId() == null) {
            deal.setId("D" + dealIdCounter.getAndIncrement());
        }
        deals.add(deal);
    }

    // ========== Notification methods ==========
    public List<Notification> getNotificationsFor(String username) {
        return notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public void addNotification(Notification n) {
        if (n.getId() == null) {
            n.setId("N" + notifIdCounter.getAndIncrement());
        }
        notifications.add(n);
    }

    // ========== Review methods ==========
    public List<Review> getReviewsForSeller(String username) {
        return reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(username) && !r.isHidden())
                .collect(Collectors.toList());
    }

    public List<Review> getAllReviews() {
        return new ArrayList<>(reviews);
    }

    public void addReview(Review r) {
        if (r.getId() == null) {
            r.setId("R" + reviewIdCounter.getAndIncrement());
        }
        reviews.add(r);
    }

    /** Validates the documented password policy for account creation and password changes. */
    public static String checkPasswordStrength(String password) {
        if (password == null || password.isEmpty()) return "Password cannot be empty.";
        if (password.length() < 8) return "Password must be at least 8 characters.";
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasUpper || !hasLower || !hasDigit) {
            return "Password should contain uppercase, lowercase and a digit.";
        }
        if (password.codePoints().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            return "Password must contain a special character.";
        }
        return "OK";
    }
}
