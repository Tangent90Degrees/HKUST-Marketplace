package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AdminReviewTest {
    @TempDir Path directory;

    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void approvalRequiresAdminAndBecomesLiveExactlyOnce() {
        DummyDataService service = new DummyDataService();
        SessionManager.login(service.authenticate("admin", "admin123", UserRole.ADMIN));
        Listing listing = service.getPendingListings().get(0);
        SessionManager.login(service.authenticate("alice", "password1", UserRole.BUYER));
        assertThrows(SecurityException.class, service::getPendingListings);
        assertThrows(SecurityException.class, () -> service.reviewListing(listing.getId(), true, null));
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());

        SessionManager.login(service.authenticate("admin", "admin123", UserRole.ADMIN));
        int notices = service.getNotificationsFor(listing.getSellerUsername()).size();
        service.reviewListing(listing.getId(), true, null);
        assertTrue(service.getLiveListings().contains(listing));
        assertFalse(service.getPendingListings().contains(listing));
        assertThrows(IllegalStateException.class, () -> service.reviewListing(listing.getId(), true, null));
        assertEquals(notices + 1, service.getNotificationsFor(listing.getSellerUsername()).size());
    }

    @Test
    void rejectionReasonAndAdminProfileSurviveRestart() {
        DummyDataService service = new DummyDataService();
        Path file = directory.resolve("marketplace.data");
        service.openLocalStorage(file);
        SessionManager.login(service.authenticate("admin", "admin123", UserRole.ADMIN));
        Listing listing = service.getPendingListings().get(0);
        assertThrows(IllegalArgumentException.class, () -> service.reviewListing(listing.getId(), false, " "));
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> service.updateAdminProfile("Administrator", "AdminPass1!", "Mismatch2!"));
        service.updateAdminProfile("Administrator", "AdminPass1!", "AdminPass1!");
        service.reviewListing(listing.getId(), false, "Photo needs replacing");

        DummyDataService reopened = new DummyDataService();
        reopened.openLocalStorage(file);
        assertEquals("Administrator", reopened.authenticate("admin", "AdminPass1!", UserRole.ADMIN).getFullName());
        Listing rejected = reopened.findListing(listing.getId());
        assertEquals(ListingStatus.REJECTED, rejected.getStatus());
        assertEquals("Photo needs replacing", rejected.getRejectionReason());
        assertTrue(reopened.getNotificationsFor(rejected.getSellerUsername()).stream()
                .anyMatch(notice -> notice.getMessage().contains(rejected.getRejectionReason())));
    }
}
