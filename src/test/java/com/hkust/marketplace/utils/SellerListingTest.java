package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SellerListingTest {
    @TempDir Path directory;

    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void sellerAndPendingListingWithPhotoSurviveRestart() {
        DummyDataService service = new DummyDataService();
        Path data = directory.resolve("marketplace.data");
        service.openLocalStorage(data);
        User seller = service.registerSeller("newSeller", "New Seller", "SellerPass1!", "SellerPass1!",
                "My bio", "Library");
        SessionManager.login(seller);
        service.updateSellerProfile("Updated Seller", "", "", "New bio", "Shaw Auditorium");

        byte[] photo = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+W3uoAAAAASUVORK5CYII=");
        Listing listing = draft();
        listing.setPhotos(List.of(photo));
        service.submitSellerListing(listing);

        DummyDataService reopened = new DummyDataService();
        reopened.openLocalStorage(data);
        User saved = reopened.authenticate("newseller", "SellerPass1!", UserRole.SELLER);
        assertEquals("New bio", saved.getBio());
        assertEquals("Shaw Auditorium", saved.getPreferredMeetup());
        Listing savedListing = reopened.findListing(listing.getId());
        assertEquals(ListingStatus.PENDING_REVIEW, savedListing.getStatus());
        assertEquals("newseller", savedListing.getSellerUsername());
        assertArrayEquals(photo, savedListing.getPhotos().get(0));
        assertTrue(reopened.getNotificationsFor("admin").stream()
                .anyMatch(notice -> notice.getMessage().contains("S1 test item")));
    }

    @Test
    void serviceRejectsWrongRoleAndInvalidListingTerms() {
        DummyDataService service = new DummyDataService();
        SessionManager.login(service.authenticate("alice", "password1", UserRole.BUYER));
        assertThrows(SecurityException.class, () -> service.submitSellerListing(draft()));
        User seller = service.registerSeller("s1seller", "Seller", "SellerPass1!", "SellerPass1!", null, null);
        SessionManager.login(seller);
        Listing listing = draft();
        listing.setLendingSeconds(4L);
        assertThrows(IllegalArgumentException.class, () -> service.submitSellerListing(listing));
        listing.setType(ListingType.SELL);
        listing.setPrice(Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> service.submitSellerListing(listing));
        assertTrue(service.getListingsBySeller("s1seller").isEmpty());
    }

    @Test
    void failedSaveRollsBackListingAndNotice() throws Exception {
        DummyDataService service = new DummyDataService();
        Path data = directory.resolve("marketplace.data");
        service.openLocalStorage(data);
        SessionManager.login(service.registerSeller("s1seller", "Seller",
                "SellerPass1!", "SellerPass1!", null, null));
        int noticeCount = service.getNotificationsFor("admin").size();
        Files.delete(data);
        Files.createDirectory(data);
        Files.createFile(data.resolve("existing-file"));

        Listing listing = draft();
        assertThrows(IllegalStateException.class, () -> service.submitSellerListing(listing));
        assertNull(listing.getId());
        assertEquals(noticeCount, service.getNotificationsFor("admin").size());
        assertTrue(service.getListingsBySeller("s1seller").isEmpty());
    }

    private static Listing draft() {
        Listing listing = new Listing();
        listing.setTitle("S1 test item");
        listing.setDescription("In good condition");
        listing.setCategory("Textbooks");
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setType(ListingType.LEND);
        listing.setLendingSeconds(5L);
        return listing;
    }
}
