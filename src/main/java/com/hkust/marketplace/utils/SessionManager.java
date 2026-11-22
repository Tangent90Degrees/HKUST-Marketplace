package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;

/**
 * Holds the authenticated actor for the desktop UI or the current service call thread.
 */
public class SessionManager {
    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    public static void login(User user) {
        CURRENT.set(user);
    }

    public static void logout() {
        CURRENT.remove();
    }

    public static User getCurrentUser() {
        return CURRENT.get();
    }

    public static boolean isLoggedIn() {
        return getCurrentUser() != null;
    }

    public static boolean isBuyer() {
        return isRole(UserRole.BUYER);
    }

    public static boolean isSeller() {
        return isRole(UserRole.SELLER);
    }

    public static boolean isAdmin() {
        return isRole(UserRole.ADMIN);
    }

    private static boolean isRole(UserRole role) {
        User actor = getCurrentUser();
        return actor != null && actor.getRole() == role;
    }
}
