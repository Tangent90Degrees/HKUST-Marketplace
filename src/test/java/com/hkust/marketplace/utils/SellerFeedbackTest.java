package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SellerFeedbackTest {
    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void sellerReplyTargetsReviewerAndFlagLinksAdminToReview() {
        DummyDataService service = new DummyDataService();
        var seller = service.authenticate("charlie", "password1", UserRole.SELLER);
        SessionManager.login(seller);
        var review = service.sellerReviews().get(0);
        assertThrows(IllegalArgumentException.class, () -> service.replyToReview(review.getId(), "  "));
        service.replyToReview(review.getId(), "Thank you");
        service.flagReview(review.getId());
        assertTrue(review.isFlagged());
        assertThrows(IllegalStateException.class, () -> service.flagReview(review.getId()));
        var alert = service.getNotificationsFor("admin").stream()
                .filter(n -> n.getTitle().equals("Review flagged")).findFirst().orElseThrow();
        assertThrows(SecurityException.class, () -> service.readOwnNotice(alert.getId()));

        SessionManager.login(service.authenticate("bob", "password1", UserRole.BUYER));
        assertTrue(service.ownNotifications().stream().anyMatch(n -> n.getMessage().contains("Thank you")));
        SessionManager.login(service.authenticate("admin", "admin123", UserRole.ADMIN));
        assertTrue(service.readOwnNotice(alert.getId()).isRead());
        assertTrue(service.noticeRecord(alert.getId()).contains(review.getListingTitle()));
    }
}
