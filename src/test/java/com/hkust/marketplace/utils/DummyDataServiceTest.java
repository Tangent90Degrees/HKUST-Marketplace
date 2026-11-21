package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DummyDataServiceTest {

    @Test
    void seededBuyerPasswordIsHashedAndVerifiable() {
        DummyDataService service = DummyDataService.getInstance();
        User alice = service.findUser("alice");

        assertNotNull(alice);
        assertNotEquals("password1", alice.getPasswordHash());
        assertTrue(service.verifyPassword(alice, "password1"));
        assertFalse(service.verifyPassword(alice, "wrong-password"));
        assertFalse(service.verifyPassword(alice, alice.getPasswordHash()));
        assertNotEquals(DummyDataService.hashPassword("password1"), alice.getPasswordHash());
    }

    @Test
    void registerUserHashesPasswordAndNormalizesUsername() {
        DummyDataService service = DummyDataService.getInstance();
        String uniqueUsername = "testuser" + System.nanoTime();
        User user = new User("  " + uniqueUsername.toUpperCase() + "  ", "Test User", "Passw0rd", UserRole.BUYER);

        service.registerUser(user);

        assertEquals(uniqueUsername.toLowerCase(), user.getUsername());
        assertTrue(service.usernameExists(uniqueUsername));
        assertNotEquals("Passw0rd", user.getPasswordHash());
        assertTrue(service.verifyPassword(uniqueUsername, "Passw0rd"));
        assertFalse(service.verifyPassword(uniqueUsername, "WrongPassw0rd"));
    }

    @Test
    void passwordStrengthRulesAreEnforced() {
        assertEquals("Password cannot be empty.", DummyDataService.checkPasswordStrength(""));
        assertEquals("Password must be at least 8 characters.", DummyDataService.checkPasswordStrength("Ab1"));
        assertEquals("Password should contain uppercase, lowercase and a digit.",
                DummyDataService.checkPasswordStrength("password"));
        assertEquals("Password should contain uppercase, lowercase and a digit.",
                DummyDataService.checkPasswordStrength("PASSWORD1"));
        assertEquals("Password must contain a special character.",
                DummyDataService.checkPasswordStrength("SecurePass1"));
        assertEquals("OK", DummyDataService.checkPasswordStrength("SecurePass1!"));
    }

    @Test
    void buyerRegistrationEnforcesUniqueNamesAcrossRolesAndDoesNotSignIn() {
        DummyDataService service = DummyDataService.getInstance();
        assertThrows(IllegalArgumentException.class,
                () -> service.registerBuyer(" ADMIN ", "Duplicate", "BuyerPass1!", "BuyerPass1!", null));
        String username = "buyer" + System.nanoTime();
        assertThrows(IllegalArgumentException.class,
                () -> service.registerBuyer(username, "Buyer", "BuyerPass1!", "Mismatch1!", null));
        assertNull(service.findUser(username));
        service.registerBuyer(username, "Buyer", "BuyerPass1!", "BuyerPass1!", "Hall 1");
        assertFalse(SessionManager.isLoggedIn());
        assertEquals("Hall 1", service.authenticate(username, "BuyerPass1!", UserRole.BUYER).getResidenceHall());
        assertThrows(IllegalArgumentException.class,
                () -> service.authenticate(username, "BuyerPass1!", UserRole.ADMIN));
    }

    @Test
    void buyerProfileRequiresItsActiveSessionAndRejectsInvalidChanges() {
        DummyDataService service = DummyDataService.getInstance();
        String username = "profile" + System.nanoTime();
        service.registerBuyer(username, "Old Name", "BuyerPass1!", "BuyerPass1!", null);
        assertThrows(SecurityException.class, () -> service.updateBuyerProfile("New Name", "", ""));
        User buyer = service.authenticate(username, "BuyerPass1!", UserRole.BUYER);
        SessionManager.login(buyer);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> service.updateBuyerProfile("New Name", "NewBuyer1!", "Mismatch1!"));
            assertEquals("Old Name", buyer.getFullName());
            assertTrue(service.verifyPassword(buyer, "BuyerPass1!"));
            service.updateBuyerProfile("New Name", "", "");
            assertEquals("New Name", buyer.getFullName());
            assertTrue(service.verifyPassword(buyer, "BuyerPass1!"));
            service.updateBuyerProfile("New Name", "NewBuyer1!", "NewBuyer1!");
            assertTrue(service.verifyPassword(buyer, "NewBuyer1!"));
            assertFalse(service.verifyPassword(buyer, "BuyerPass1!"));
        } finally {
            SessionManager.logout();
        }
    }
}
