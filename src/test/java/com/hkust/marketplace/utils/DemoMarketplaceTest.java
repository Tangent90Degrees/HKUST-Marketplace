package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DemoMarketplaceTest {
    @TempDir Path directory;

    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void importAndClearArePersistedInDemoStoreOnly() throws Exception {
        Path regular = directory.resolve("marketplace.data");
        Path demo = directory.resolve("marketplace-demo.data");
        var service = new DummyDataService();
        service.openLocalStorage(regular);
        service.registerBuyer("regular-buyer", "Regular buyer", "Str0ngPass!", "Str0ngPass!", null);
        byte[] regularContent = Files.readAllBytes(regular);
        assertThrows(IllegalStateException.class, service::clearDemoData);

        service.openDemoStorage(demo);
        assertFalse(service.usernameExists("regular-buyer"));
        service.registerBuyer("demo-buyer", "Demo buyer", "Str0ngPass!", "Str0ngPass!", null);
        service.clearDemoData();
        var reopened = new DummyDataService();
        reopened.openDemoStorage(demo);
        assertTrue(reopened.getLiveListings().isEmpty());
        assertTrue(reopened.usernameExists("demo-buyer"));

        reopened.importDemoData();
        service.openDemoStorage(demo);
        service.signIn("charlie", "password1", UserRole.SELLER);
        var thread = service.sellerInbox().get(0);
        assertEquals("alice", thread.buyer());
        assertTrue(thread.latest().text().contains("MacBook charger"));
        assertTrue(service.usernameExists("demo-buyer"));
        assertArrayEquals(regularContent, Files.readAllBytes(regular));
    }

    @Test
    void buyerMessagesAppearInOwningSellerInboxAfterRestartWithoutAnOffer() {
        Path demo = directory.resolve("marketplace-demo.data");
        var service = new DummyDataService();
        service.openDemoStorage(demo);
        service.signIn("alice", "password1", UserRole.BUYER);
        service.sendBuyerMessage("L101", "Is it still available?");
        service.sendBuyerMessage("L101", "Can I collect it at the library?");
        assertTrue(service.buyerDeals().stream().noneMatch(deal -> "L101".equals(deal.getListingId())));

        var reopened = new DummyDataService();
        reopened.openDemoStorage(demo);
        reopened.signIn("diana", "password1", UserRole.SELLER);
        assertTrue(reopened.sellerInbox().isEmpty());
        reopened.signIn("charlie", "password1", UserRole.SELLER);
        var inbox = reopened.sellerInbox();
        assertEquals(1, inbox.size());
        assertEquals("Can I collect it at the library?", inbox.get(0).latest().text());
        assertEquals(2, reopened.sellerChat(inbox.get(0).listingId(), inbox.get(0).buyer(), "").size());
    }
}
