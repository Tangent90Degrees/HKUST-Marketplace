package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class BuyerMarketplaceTest {
    @TempDir Path directory;

    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void buyerCanSearchViewAndPersistPrivateChatAndOffer() {
        DummyDataService service = new DummyDataService();
        Path file = directory.resolve("marketplace.data");
        service.openLocalStorage(file);
        SessionManager.login(service.authenticate("alice", "password1", UserRole.BUYER));
        var listing = service.browse("", null, ListingType.SELL, null, 0.0, null,
                DummyDataService.CatalogOrder.PRICE_LOW).get(0);
        int before = listing.getViewCount();
        service.openBuyerListing(listing.getId());
        assertEquals(before + 1, listing.getViewCount());
        service.sendBuyerMessage(listing.getId(), "Can we meet at the library?");
        assertThrows(IllegalArgumentException.class,
                () -> service.submitBuyerProposal(listing.getId(), -1.0, null, null, true));
        var offer = service.submitBuyerProposal(listing.getId(), 0.0, null, null, true);
        assertEquals(offer.getId(), service.buyerChat(listing.getId(), "proposal").get(0).proposalId());
        service.reportListing(listing.getId(), "Please review the description");
        assertTrue(service.getLiveListings().contains(listing));

        DummyDataService reopened = new DummyDataService();
        reopened.openLocalStorage(file);
        SessionManager.login(reopened.authenticate("alice", "password1", UserRole.BUYER));
        assertEquals(1, reopened.buyerChat(listing.getId(), "LIBRARY").size());
        SessionManager.login(reopened.authenticate("bob", "password1", UserRole.BUYER));
        assertTrue(reopened.buyerChat(listing.getId(), null).isEmpty());
        assertTrue(reopened.getNotificationsFor("admin").stream()
                .anyMatch(n -> n.getMessage().contains("Please review the description")));
    }

    @Test
    void concurrentGiveawayClaimsRecordExactlyOneWinnerAndRejectShortLoans() throws Exception {
        DummyDataService service = new DummyDataService();
        User alice = service.authenticate("alice", "password1", UserRole.BUYER);
        User bob = service.authenticate("bob", "password1", UserRole.BUYER);
        SessionManager.login(alice);
        var loan = service.browse("", null, ListingType.LEND, null, null, null,
                DummyDataService.CatalogOrder.NEWEST).get(0);
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> service.submitBuyerProposal(loan.getId(), null, now, now.plusSeconds(4), false));
        var gift = service.browse("", null, ListingType.GIVEAWAY, null, null, null,
                DummyDataService.CatalogOrder.NEWEST).get(0);
        CountDownLatch ready = new CountDownLatch(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(claim(service, gift.getId(), alice, ready));
            var second = pool.submit(claim(service, gift.getId(), bob, ready));
            assertNotEquals(first.get(), second.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Boolean> claim(DummyDataService service, String id, User buyer, CountDownLatch ready) {
        return () -> {
            SessionManager.login(buyer);
            ready.countDown();
            ready.await();
            try {
                service.submitBuyerProposal(id, null, null, null, false);
                return true;
            } catch (IllegalStateException unavailable) {
                return false;
            } finally {
                SessionManager.logout();
            }
        };
    }
}
