package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SellerProposalTest {
    @AfterEach
    void logout() {
        SessionManager.logout();
    }

    @Test
    void sellerCanCounterInSharedChatAndAcceptRevisedOffer() {
        DummyDataService service = new DummyDataService();
        var buyer = service.authenticate("alice", "password1", UserRole.BUYER);
        SessionManager.login(buyer);
        var listing = service.browse("", null, ListingType.SELL, null, null, null,
                DummyDataService.CatalogOrder.NEWEST).get(0);
        var proposal = service.submitBuyerProposal(listing.getId(), 12.0, null, null, false);
        var seller = service.authenticate(listing.getSellerUsername(), "password1", UserRole.SELLER);
        SessionManager.login(seller);
        service.sendSellerMessage(listing.getId(), "alice", "Library tomorrow?");
        service.decideProposal(proposal.getId(), DummyDataService.SellerDecision.COUNTER, 14.0, null, null, true);

        SessionManager.login(buyer);
        assertEquals(1, service.buyerChat(listing.getId(), "counter").size());
        assertEquals(1, service.buyerChat(listing.getId(), "library").size());
        assertEquals("Countered", proposal.getStatus());
        var revised = service.submitBuyerProposal(listing.getId(), 14.0, null, null, false);
        SessionManager.login(seller);
        assertThrows(IllegalStateException.class, () -> service.changeSellerListing(listing.getId(), null, false));
        service.decideProposal(revised.getId(), DummyDataService.SellerDecision.ACCEPT, null, null, null, false);
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertEquals("Cancelled", proposal.getStatus());
        assertEquals("Accepted", revised.getStatus());
        assertThrows(IllegalStateException.class, () -> service.decideProposal(
                revised.getId(), DummyDataService.SellerDecision.ACCEPT, null, null, null, false));
    }
}
