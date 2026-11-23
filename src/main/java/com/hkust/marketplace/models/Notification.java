package com.hkust.marketplace.models;

import java.time.LocalDateTime;

public class Notification implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username; // recipient
    private String title;
    private String message;
    private String category; // Offer, Chat, System, Rating, etc.
    private LocalDateTime timestamp;
    private boolean read;
    private Reference reference;
    private String recordId;

    public enum Reference { LISTING, DEAL, REVIEW, REPORT, ACCOUNT }

    public Notification() {
        this.timestamp = LocalDateTime.now();
        this.read = false;
    }

    public Notification(String username, String title, String message, String category) {
        this();
        this.username = username;
        this.title = title;
        this.message = message;
        this.category = category;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Reference getReference() { return reference; }
    public String getRecordId() { return recordId; }

    public Notification link(Reference reference, String id) {
        this.reference = reference;
        this.recordId = id;
        return this;
    }
}
