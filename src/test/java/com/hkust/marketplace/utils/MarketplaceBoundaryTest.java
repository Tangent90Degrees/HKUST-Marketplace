package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceBoundaryTest {
    private final DummyDataService service = new DummyDataService();

    @AfterEach
    void logout() { SessionManager.logout(); }

    private void buyer(String name) { service.signIn(name, "password1", UserRole.BUYER); }
    private void seller(String name) { service.signIn(name, "password1", UserRole.SELLER); }
    private void admin() { service.signIn("admin", "admin123", UserRole.ADMIN); }

    @Test
    void credentialsAndSessionsRejectMissingInvalidAndInactiveAccounts() {
        assertFalse(service.verifyPassword("missing", "password1"));
        assertFalse(service.verifyPassword("alice", null));
        assertEquals("", DummyDataService.hashPassword(null));
        assertEquals("", DummyDataService.hashPassword("  "));
        assertFalse(service.usernameExists(null));
        assertThrows(IllegalArgumentException.class, () -> service.signIn("", "password1", UserRole.BUYER));
        assertThrows(IllegalArgumentException.class, () -> service.signIn("alice", "", UserRole.BUYER));
        assertThrows(IllegalArgumentException.class, () -> service.signIn("alice", null, UserRole.BUYER));
        assertThrows(SecurityException.class, service::ownNotifications);
        assertThrows(SecurityException.class, () -> service.listingForCurrentActor("L100"));
        assertFalse(SessionManager.isBuyer());
        buyer("alice");
        assertTrue(SessionManager.isBuyer());
        assertFalse(SessionManager.isSeller());
        assertFalse(SessionManager.isAdmin());
        User alice = service.findUser("alice");
        alice.setActive(false);
        assertFalse(SessionManager.isBuyer());
        assertThrows(SecurityException.class, service::buyerDeals);
        assertThrows(IllegalArgumentException.class, () -> buyer("alice"));
        alice.setActive(true);
        alice.setRole(UserRole.SELLER);
        assertThrows(SecurityException.class, service::buyerDeals);
        alice.setRole(UserRole.BUYER);
        assertThrows(SecurityException.class, new DummyDataService()::buyerDeals);
        assertEquals("Unlisted seller", service.sellerDisplayName("Unlisted seller"));
    }

    @Test
    void incompleteAndCorruptCredentialRecordsFailClosed() {
        User user = new User("buyer", "Buyer", null, UserRole.BUYER);
        for (String encoded : new String[]{null, "plaintext", "pbkdf2:only-one-field",
                "pbkdf2:??:??", "pbkdf2:AA==:AA==", "pbkdf2:AAAAAAAAAAAAAAAAAAAAAA==:AA=="}) {
            user.setPasswordHash(encoded);
            assertFalse(service.verifyPassword(user, "password1"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(null));
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(userNamed(null)));
        assertThrows(IllegalArgumentException.class, () -> service.registerUser(userNamed(" ")));
    }

    @Test
    void accountRegistrationAndEditingValidateNamesPasswordsAndLocations() {
        assertThrows(IllegalArgumentException.class, () -> service.registerBuyer("new", null, "Strong123!", "Strong123!", null));
        assertThrows(IllegalArgumentException.class, () -> service.registerBuyer("new", " ", "Strong123!", "Strong123!", null));
        assertThrows(IllegalArgumentException.class, () -> service.registerBuyer("new", "New", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.registerBuyer("new", "New", "Password !", "Password !", null));
        assertThrows(IllegalArgumentException.class, () -> service.registerBuyer("new", "New", "Password1 ", "Password1 ", null));
        assertThrows(IllegalArgumentException.class, () -> service.registerSeller("new", "New", "Strong123!", "Strong123!", null, "Invalid"));
        service.registerAndSignInSeller("new", "New", "Strong123!", "Strong123!", null, null);
        assertTrue(SessionManager.isSeller());
        service.updateSellerProfile("New name", "", "", null, null);
        assertEquals("", service.findUser("new").getBio());
        assertThrows(IllegalArgumentException.class, () -> service.updateSellerProfile("New name", "", "typo", null, null));
    }

    @Test
    void catalogCombinesFiltersAndRejectsInvalidPriceRanges() {
        buyer("alice");
        assertTrue(browse(null, null, null, null, 500.0, null).isEmpty());
        assertTrue(browse(null, null, null, null, null, 1.0).isEmpty());
        assertTrue(browse(null, "Furniture", ListingType.SELL, "New", null, null).isEmpty());
        assertEquals(1, browse("CHARGER", "Electronics", ListingType.SELL, "Used", 200.0, 300.0).size());
        assertEquals(1, browse(null, null, null, null, 0.0, 300.0).size());
        assertThrows(IllegalArgumentException.class, () -> browse(null, null, null, null, Double.NaN, null));
        assertThrows(IllegalArgumentException.class, () -> browse(null, null, null, null, -1.0, null));
        assertThrows(IllegalArgumentException.class, () -> browse(null, null, null, null, null, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> browse(null, null, null, null, null, -1.0));
        assertThrows(IllegalArgumentException.class, () -> browse(null, null, null, null, 9.0, 8.0));
        assertThrows(IllegalStateException.class, () -> service.openBuyerListing("missing"));
        assertThrows(IllegalStateException.class, () -> service.openBuyerListing("L104"));
        assertEquals("L101", service.listingForCurrentActor("L101").getId());
    }

    @Test
    void sellerDraftValidationHandlesEveryRequiredFieldBeforeSaving() {
        seller("charlie");
        List<java.util.function.Consumer<Listing>> invalid = List.of(
                l -> l.setType(null), l -> l.setTitle(null), l -> l.setTitle(" "),
                l -> l.setDescription(null), l -> l.setDescription(" "), l -> l.setCategory(null),
                l -> l.setCategory("invalid"), l -> l.setCondition(null), l -> l.setCondition("invalid"),
                l -> l.setMeetupLocation(null), l -> l.setMeetupLocation("invalid"), l -> l.setPrice(null),
                l -> l.setPrice(0.0), l -> l.setType(ListingType.LEND),
                l -> l.setPhotos(Collections.nCopies(7, new byte[]{1})),
                l -> l.setPhotos(List.of(new byte[0])), l -> l.setPhotos(List.of(new byte[5 * 1024 * 1024 + 1])),
                l -> l.setId("already assigned"));
        int before = service.ownSellerListings().size();
        for (var change : invalid) {
            Listing listing = draft("Invalid");
            change.accept(listing);
            assertThrows(IllegalArgumentException.class, () -> service.submitSellerListing(listing));
        }
        assertEquals(before, service.ownSellerListings().size());
        Listing listing = draft("Valid loan");
        listing.setType(ListingType.LEND);
        listing.setLendingDays(2);
        assertEquals(172800L, listing.getLendingSeconds());
        listing.setLendingSeconds(60L);
        assertEquals(60L, listing.getLendingSeconds());
        assertNull(draft("Sale").getLendingSeconds());
        service.submitSellerListing(listing);
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
    }

    @Test
    void adminPublishingEditingAndTaketownPreserveIdentity() {
        admin();
        assertThrows(IllegalArgumentException.class, () -> service.adminCreateListing("bob", draft("Wrong owner")));
        assertThrows(IllegalStateException.class, () -> service.adminCreateListing("ghost", draft("No owner")));
        assertThrows(IllegalStateException.class, () -> service.adminCreateListing("admin", draft("Wrong account")));
        service.moderateAccount("diana", false);
        assertThrows(IllegalArgumentException.class, () -> service.adminCreateListing("diana", draft("Inactive owner")));
        assertThrows(IllegalStateException.class, () -> service.moderateAccount("diana", false));
        service.moderateAccount("diana", true);
        assertThrows(IllegalStateException.class, () -> service.adminCreateListing("diana", draft("Deleted owner")));

        Listing listing = draft("Spare keyboard");
        service.adminCreateListing("charlie", listing);
        String id = listing.getId();
        assertEquals("admin", listing.getCreatedByAdmin());
        assertEquals(ListingStatus.LIVE, service.listingForCurrentActor(id).getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.adminCreateListing("charlie", listing));
        assertThrows(IllegalArgumentException.class, () -> service.adminTakeDown(id, null));
        assertThrows(IllegalArgumentException.class, () -> service.adminTakeDown(id, " "));
        assertThrows(IllegalStateException.class, () -> service.adminEditListing("missing", draft("Moved")));
        buyer("alice");
        Deal pending = service.submitBuyerProposal(id, 40.0, null, null, false);
        admin();
        service.adminEditListing(id, draft("Edited keyboard"));
        assertEquals("Cancelled", pending.getStatus());
        assertEquals("charlie", service.listingForCurrentActor(id).getSellerUsername());
        service.adminTakeDown(id, "No longer available");
        assertThrows(IllegalArgumentException.class, () -> service.adminTakeDown(id, "Repeated"));
        assertThrows(IllegalStateException.class, () -> service.reviewListing("missing", true, null));
    }

    @Test
    void sellerChatHandlesExistingAndNewOffersAndRejectsOtherConversations() {
        String id = "L101";
        buyer("alice");
        assertThrows(IllegalArgumentException.class, () -> service.sendBuyerMessage(id, null));
        assertThrows(IllegalArgumentException.class, () -> service.sendBuyerMessage(id, " "));
        assertThrows(IllegalArgumentException.class, () -> service.reportListing(id, null));
        assertThrows(IllegalArgumentException.class, () -> service.reportListing(id, " "));
        assertThrows(IllegalArgumentException.class, () -> service.submitBuyerProposal(id, null, null, null, false));
        assertThrows(IllegalArgumentException.class, () -> service.submitBuyerProposal(id, Double.NaN, null, null, false));
        service.sendBuyerMessage(id, "Available today?");
        seller("diana");
        assertThrows(SecurityException.class, () -> service.listingForCurrentActor(id));
        seller("charlie");
        assertThrows(SecurityException.class, () -> service.listingForCurrentActor("missing"));
        assertThrows(SecurityException.class, () -> service.sellerChat(id, "bob", ""));
        assertThrows(SecurityException.class, () -> service.sendSellerMessage(id, "bob", "Hello"));
        assertThrows(IllegalStateException.class, () -> service.offerInSellerChat(id, "bob", 33.0, null, null));
        assertEquals(1, service.sellerChat(id, "alice", "today").size());
        service.offerInSellerChat(id, "alice", 30.0, null, null);
        assertTrue(service.sellerChat(id, "alice", "seller offer").stream().anyMatch(m -> m.proposalId() != null));
        buyer("alice");
        Deal pending = service.submitBuyerProposal(id, 25.0, null, null, false);
        seller("charlie");
        service.offerInSellerChat(id, "alice", 28.0, null, null);
        assertEquals("Countered", pending.getStatus());
        assertEquals(28.0, pending.getOfferPrice());
        buyer("bob");
        Deal second = service.submitBuyerProposal(id, 26.0, null, null, false);
        seller("charlie");
        service.decideProposal(second.getId(), DummyDataService.SellerDecision.DECLINE, null, null, null, true);
        assertEquals("Declined", second.getStatus());
    }

    @Test
    void sellerEditsAndRemovesListingsWithAppropriateRestriction() {
        seller("charlie");
        assertFalse(service.sellerChangeRestricted("L101"));
        assertTrue(service.sellerChangeRestricted("L100"));
        buyer("alice");
        Deal pending = service.submitBuyerProposal("L101", 30.0, null, null, false);
        seller("charlie");
        assertTrue(service.sellerChangeRestricted("L101"));
        assertThrows(IllegalStateException.class, () -> service.changeSellerListing("L101", draft("Denied"), false));
        service.changeSellerListing("L101", draft("New title"), true);
        assertEquals("New title", service.listingForCurrentActor("L101").getTitle());
        service.changeSellerListing("L101", null, true);
        assertEquals(ListingStatus.REMOVED, service.listingForCurrentActor("L101").getStatus());
        assertThrows(IllegalStateException.class, () -> service.changeSellerListing("L101", null, true));
        assertThrows(IllegalStateException.class, () -> service.decideProposal(pending.getId(),
                DummyDataService.SellerDecision.ACCEPT, null, null, null, false));
        buyer("alice");
        assertTrue(service.ownNotifications().stream().anyMatch(n -> n.getMessage().contains("was removed")));
        assertThrows(IllegalStateException.class, () -> service.submitBuyerProposal("L101", 20.0, null, null, false));
    }

    @Test
    void noticeLinksAndAdminFiltersReturnTheRightRecords() {
        buyer("alice");
        service.reportListing("L101", "Check label");
        service.reportListing("L102", "Check photo");
        var offer = service.submitBuyerProposal("L101", 20.0, null, null, false);
        seller("charlie");
        var notice = service.ownNotifications().stream().filter(n -> offer.getId().equals(n.getRecordId())).findFirst().orElseThrow();
        assertTrue(service.noticeRecord(notice.getId()).contains("HK$ 20"));
        assertTrue(service.readOwnNotice(notice.getId()).isRead());
        assertTrue(service.readOwnNotice(notice.getId()).isRead());
        assertThrows(SecurityException.class, () -> service.readOwnNotice(null));
        admin();
        service.adminEditUser("charlie", "charlie", "Charlie Lee", null, "Updated", "Library");
        assertThrows(IllegalArgumentException.class, () -> service.adminEditUser("charlie", "", "Charlie", null, "", null));
        assertThrows(IllegalArgumentException.class, () -> service.adminEditUser("charlie", "bob", "Charlie", null, "", null));
        String report = noticeId(Notification.Reference.REPORT, "L101");
        assertTrue(service.noticeRecord(report).contains("Check label"));
        assertFalse(service.noticeRecord(report).contains("Check photo"));
        assertTrue(service.noticeRecord(noticeId(Notification.Reference.ACCOUNT, "charlie")).contains("Charlie"));
        assertTrue(service.noticeRecord(noticeId(Notification.Reference.LISTING, "L104")).contains("PENDING_REVIEW"));
        assertThrows(IllegalArgumentException.class, () -> service.adminRatings(null, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.adminRatings(null, 6, null, null));
        assertTrue(service.adminRatings("charlie", null, null, LocalDate.now().plusDays(1)).size() > 0);
        service.hideRating(service.adminRatings(null, null, null, null).get(0).getId());
        assertThrows(IllegalStateException.class, () -> service.hideRating(service.adminRatings(null, null, null, null).get(0).getId()));
        assertThrows(IllegalStateException.class, () -> service.hideRating("missing"));
    }

    @Test
    void claimsAndManualLoanReturnsUseTheirDedicatedPaths() {
        buyer("alice");
        assertThrows(IllegalStateException.class, () -> service.submitBuyerProposal("L102", null, null, null, true));
        Deal claim = service.submitBuyerProposal("L102", null, null, null, false);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong"));
        assertThrows(IllegalArgumentException.class, () -> service.submitBuyerProposal("L103", null, null, now, false));
        assertThrows(IllegalArgumentException.class, () -> service.submitBuyerProposal("L103", null, now, null, false));
        Deal loan = service.submitBuyerProposal("L103", null, now.minusHours(1), now.plusHours(1), false);
        seller("diana");
        assertThrows(IllegalArgumentException.class, () -> service.decideProposal(claim.getId(),
                DummyDataService.SellerDecision.COUNTER, null, null, null, false));
        service.decideProposal(claim.getId(), DummyDataService.SellerDecision.ACCEPT, null, null, null, false);
        service.decideProposal(loan.getId(), DummyDataService.SellerDecision.ACCEPT, null, null, null, false);
        assertEquals(ListingStatus.LENT, service.listingForCurrentActor("L103").getStatus());
        buyer("alice");
        service.finishBuyerDeal(claim.getId());
        assertEquals(ListingStatus.GIVEN, service.listingForCurrentActor("L102").getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.reviewCompletedDeal(claim.getId(), 0, ""));
        assertThrows(IllegalArgumentException.class, () -> service.reviewCompletedDeal(claim.getId(), 6, ""));
        service.reviewCompletedDeal(claim.getId(), 4, null);
        service.finishBuyerDeal(loan.getId());
        assertEquals("Returned", loan.getStatus());
        assertEquals(ListingStatus.LIVE, service.listingForCurrentActor("L103").getStatus());
    }

    private List<Listing> browse(String query, String category, ListingType type, String condition, Double low, Double high) {
        return service.browse(query, category, type, condition, low, high, DummyDataService.CatalogOrder.PRICE_HIGH);
    }

    private String noticeId(Notification.Reference reference, String record) {
        return service.ownNotifications().stream()
                .filter(n -> reference == n.getReference() && record.equals(n.getRecordId()))
                .findFirst().orElseThrow().getId();
    }

    private static User userNamed(String name) { return new User(name, "Buyer", "pass", UserRole.BUYER); }

    private static Listing draft(String title) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Available on campus");
        listing.setCategory("Electronics");
        listing.setCondition("Like New");
        listing.setMeetupLocation("Library");
        listing.setType(ListingType.SELL);
        listing.setPrice(50.0);
        return listing;
    }
}
