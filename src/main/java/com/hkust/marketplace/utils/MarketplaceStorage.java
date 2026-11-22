package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/** Stores an atomic snapshot in the local application directory. */
final class MarketplaceStorage {
    record Snapshot(Map<String, User> users, Map<String, Listing> listings, List<Deal> deals,
                    List<Notification> notifications, List<Review> reviews,
                    List<ChatMessage> messages, List<ListingReport> reports,
                    int nextListing, int nextDeal, int nextNotification, int nextReview) implements Serializable {}

    private final Path file;

    MarketplaceStorage(Path file) {
        this.file = file.toAbsolutePath();
    }

    /** Returns the saved data, or null before the first save. */
    Snapshot load() throws IOException {
        if (Files.notExists(file)) {
            return null;
        }
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(file))) {
            input.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "maxdepth=32;maxarray=5242880;com.hkust.marketplace.models.*;"
                            + "com.hkust.marketplace.utils.MarketplaceStorage$Snapshot;"
                            + "java.util.*;java.lang.*;java.time.*;!*"));
            if (input.readObject() instanceof Snapshot snapshot) {
                return snapshot;
            }
            throw new IOException("Unexpected marketplace data format.");
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to read local marketplace data.", e);
        }
    }

    /** Replaces the last saved version only after the new snapshot is written. */
    void save(Snapshot snapshot) throws IOException {
        Path directory = Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(directory, "marketplace-", ".tmp");
        try {
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temporary))) {
                output.writeObject(snapshot);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
