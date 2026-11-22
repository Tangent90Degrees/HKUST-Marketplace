package com.hkust.marketplace.models;

import java.io.Serializable;
import java.time.LocalDateTime;

/** A retained message in one listing-specific buyer/seller conversation. */
public record ChatMessage(String listingId, String buyer, String seller, String sender,
                          String text, String proposalId, LocalDateTime sentAt) implements Serializable {}
