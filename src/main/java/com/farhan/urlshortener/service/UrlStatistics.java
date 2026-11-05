package com.farhan.urlshortener.service;

import java.time.LocalDateTime;

public record UrlStatistics(
        String shortCode,
        String longUrl,
        long clickCount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
