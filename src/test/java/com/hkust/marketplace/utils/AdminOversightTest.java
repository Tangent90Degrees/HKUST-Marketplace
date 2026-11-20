package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class AdminOversightTest {
    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void onlyAdminCanFilterDealsAndDateBoundsIncludeBothEnds() {
        DummyDataService service = new DummyDataService();
        service.signIn("alice", "password1", UserRole.BUYER);
        assertThrows(SecurityException.class, () -> service.adminDealRecords("", null, null, null, null));
        var lender = service.browse("", null, ListingType.LEND, null, null, null,
                DummyDataService.CatalogOrder.NEWEST).get(0);
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Hong_Kong")).plusDays(1);
        var loan = service.submitBuyerProposal(lender.getId(), null, date.atStartOfDay(),
                date.atStartOfDay().plusSeconds(5), false);
        service.signIn(lender.getSellerUsername(), "password1", UserRole.SELLER);
        service.decideProposal(loan.getId(), DummyDataService.SellerDecision.ACCEPT, null, null, null, false);
        service.signIn("admin", "admin123", UserRole.ADMIN);
        assertTrue(service.adminDealRecords("alice", ListingType.LEND, "Accepted", date, date).contains(loan));
        assertTrue(service.adminDealRecords("alice", ListingType.LEND, null, date.plusDays(1), null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.adminDealRecords(null, null, null, date, date.minusDays(1)));
    }

    @Test
    void hidingFeedbackRetainsReplyForAdminAndExcludesItFromPublicResults() {
        DummyDataService service = new DummyDataService();
        service.signIn("charlie", "password1", UserRole.SELLER);
        var review = service.sellerReviews().get(0);
        service.replyToReview(review.getId(), "Thanks.");
        assertThrows(SecurityException.class, () -> service.hideRating(review.getId()));
        service.signIn("admin", "admin123", UserRole.ADMIN);
        LocalDate date = review.getCreatedAt().toLocalDate();
        assertEquals(1, service.adminRatings("bob", 5, date, date).size());
        service.hideRating(review.getId());
        assertEquals("Thanks.", service.adminRatings("charlie", null, null, null).get(0).getSellerReply());
        assertTrue(service.getReviewsForSeller("charlie").isEmpty());
        service.signIn("charlie", "password1", UserRole.SELLER);
        assertThrows(IllegalStateException.class, () -> service.replyToReview(review.getId(), "More"));
    }
}
