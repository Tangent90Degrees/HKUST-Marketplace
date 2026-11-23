package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DealLifecycleTest {
    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void onlyBuyerCanCompleteAndReviewTheirSale() {
        DummyDataService service = new DummyDataService();
        SessionManager.login(service.authenticate("alice", "password1", UserRole.BUYER));
        var deal = service.buyerDeals().get(0);
        assertThrows(IllegalArgumentException.class, () -> service.reviewCompletedDeal(deal.getId(), 5, "Early"));
        service.finishBuyerDeal(deal.getId());
        assertEquals(ListingStatus.SOLD, service.findListing(deal.getListingId()).getStatus());
        assertTrue(deal.isCompleted());
        assertThrows(IllegalStateException.class, () -> service.finishBuyerDeal(deal.getId()));
        service.reviewCompletedDeal(deal.getId(), 4, "Successful exchange");
        assertTrue(service.getReviewsForSeller(deal.getSellerUsername()).stream()
                .anyMatch(review -> deal.getId().equals(review.getDealId())));
        SessionManager.login(service.authenticate("bob", "password1", UserRole.BUYER));
        assertThrows(SecurityException.class, () -> service.finishBuyerDeal(deal.getId()));
    }

    @Test
    void scheduledBorrowingActivatesAndReturnsOnlyOnceWithoutASession() {
        DummyDataService service = new DummyDataService();
        var buyer = service.authenticate("alice", "password1", UserRole.BUYER);
        SessionManager.login(buyer);
        var listing = service.browse("", null, ListingType.LEND, null, null, null,
                DummyDataService.CatalogOrder.NEWEST).get(0);
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusMinutes(5);
        LocalDateTime end = start.plusSeconds(5);
        var deal = service.submitBuyerProposal(listing.getId(), null, start, end, false);
        SessionManager.login(service.authenticate(listing.getSellerUsername(), "password1", UserRole.SELLER));
        service.decideProposal(deal.getId(), DummyDataService.SellerDecision.ACCEPT, null, null, null, false);
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        SessionManager.logout();
        service.advanceBorrowings(start);
        assertEquals(ListingStatus.LENT, listing.getStatus());
        service.advanceBorrowings(end);
        service.advanceBorrowings(end.plusSeconds(1));
        assertEquals("Returned", deal.getStatus());
        assertEquals(end, deal.getCompletedAt());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        SessionManager.login(buyer);
        assertEquals(1, service.ownNotifications().stream().filter(n -> n.getTitle().equals("Borrowing due soon")).count());
        service.reviewCompletedDeal(deal.getId(), 5, "Returned borrowing");
    }
}
