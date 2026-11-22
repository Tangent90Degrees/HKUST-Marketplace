package com.hkust.marketplace.models;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A buyer's moderation report about one listing. */
public record ListingReport(String listingId, String buyer, String reason,
                            LocalDateTime filedAt) implements Serializable {}
