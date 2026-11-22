package com.hkust.marketplace.models;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Deal implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String listingId;
    private String listingTitle;
    private String buyerUsername;
    private String sellerUsername;
    private ListingType dealType;
    private String status; // Pending, Accepted, Completed, Returned, Cancelled
    private Double offerPrice;
    private LocalDate borrowStart;
    private LocalDate borrowEnd;
    private LocalDateTime requestedStart;
    private LocalDateTime requestedEnd;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private boolean rated;

    public Deal() {
        this.createdAt = LocalDateTime.now();
        this.status = "Pending";
        this.rated = false;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }

    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }

    public String getBuyerUsername() { return buyerUsername; }
    public void setBuyerUsername(String buyerUsername) { this.buyerUsername = buyerUsername; }

    public String getSellerUsername() { return sellerUsername; }
    public void setSellerUsername(String sellerUsername) { this.sellerUsername = sellerUsername; }

    public ListingType getDealType() { return dealType; }
    public void setDealType(ListingType dealType) { this.dealType = dealType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getOfferPrice() { return offerPrice; }
    public void setOfferPrice(Double offerPrice) { this.offerPrice = offerPrice; }

    public LocalDate getBorrowStart() { return borrowStart; }
    public void setBorrowStart(LocalDate borrowStart) { this.borrowStart = borrowStart; }

    public LocalDate getBorrowEnd() { return borrowEnd; }
    public void setBorrowEnd(LocalDate borrowEnd) { this.borrowEnd = borrowEnd; }

    public LocalDateTime getRequestedStart() {
        return requestedStart != null || borrowStart == null ? requestedStart : borrowStart.atStartOfDay();
    }
    public LocalDateTime getRequestedEnd() {
        return requestedEnd != null || borrowEnd == null ? requestedEnd : borrowEnd.atStartOfDay();
    }
    public void setRequestedPeriod(LocalDateTime start, LocalDateTime end) {
        requestedStart = start;
        requestedEnd = end;
        borrowStart = start == null ? null : start.toLocalDate();
        borrowEnd = end == null ? null : end.toLocalDate();
    }

    public String terms() {
        return switch (dealType) {
            case SELL -> "HK$ " + offerPrice;
            case LEND -> getRequestedStart() + " to " + getRequestedEnd();
            case GIVEAWAY -> "Giveaway claim";
        };
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public boolean isRated() { return rated; }
    public void setRated(boolean rated) { this.rated = rated; }
}
