package com.farhan.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
@Table(name = "urls", indexes = {
        @Index(name = "idx_urls_short_code", columnList = "short_code", unique = true),
        @Index(name = "idx_urls_expires_at", columnList = "expires_at")
})
public class Url {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 16)
    private String shortCode;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Nullable during schema evolution so Hibernate can add this column to a
    // populated Phase 2 table without requiring a destructive table rebuild.
    // New rows receive an expiry in the service and in the @PrePersist fallback.
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    protected Url() {
        // Required by JPA.
    }

    public Url(String shortCode, String longUrl) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
    }

    @PrePersist
    void setCreatedAtIfMissing() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusDays(30);
        }
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public long getClickCount() {
        return clickCount;
    }

    public void setClickCount(long clickCount) {
        this.clickCount = clickCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
