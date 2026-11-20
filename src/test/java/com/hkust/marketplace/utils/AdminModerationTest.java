package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminModerationTest {
    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void takedownClosesProposalsAndNotifiesOnlyBuyersWithOpenOffers() {
        DummyDataService service = new DummyDataService();
        var alice = service.authenticate("alice", "password1", UserRole.BUYER);
        SessionManager.login(alice);
        var listing = service.browse("", null, ListingType.SELL, null, null, null,
                DummyDataService.CatalogOrder.NEWEST).get(0);
        var proposal = service.submitBuyerProposal(listing.getId(), 40.0, null, null, false);
        service.sendBuyerMessage(listing.getId(), "Retain this chat");
        assertThrows(SecurityException.class, () -> service.adminTakeDown(listing.getId(), "Incorrect category"));
        int bobNotices = service.getNotificationsFor("bob").size();
        SessionManager.login(service.authenticate("admin", "admin123", UserRole.ADMIN));
        service.adminTakeDown(listing.getId(), "Incorrect category");
        assertEquals(ListingStatus.REMOVED, listing.getStatus());
        assertEquals("Incorrect category", listing.getRemovalReason());
        assertEquals("Cancelled", proposal.getStatus());
        assertEquals(bobNotices, service.getNotificationsFor("bob").size());
        assertTrue(service.getNotificationsFor("alice").stream()
                .anyMatch(n -> n.getMessage().contains("Incorrect category")));
        SessionManager.login(alice);
        assertEquals(1, service.buyerChat(listing.getId(), "retain").size());
    }

    @Test
    void privilegedAccountChangesRetainIdentityAndBlockActiveDealRemoval() {
        DummyDataService service = new DummyDataService();
        SessionManager.login(service.authenticate("admin", "admin123", UserRole.ADMIN));
        assertThrows(IllegalStateException.class, () -> service.moderateAccount("alice", true));
        service.adminEditUser("alice", "renamed", "Alice Updated", "Hall 1", null, null);
        assertNull(service.findUser("alice"));
        assertEquals("renamed", service.getDealsForBuyer("renamed").get(0).getBuyerUsername());
        service.moderateAccount("bob", true);
        assertThrows(IllegalArgumentException.class, () -> service.authenticate("bob", "password1", UserRole.BUYER));
        assertTrue(service.getAllUsers().stream().noneMatch(user -> user.getUsername().equals("bob")));
    }
}
