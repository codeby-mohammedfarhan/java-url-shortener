package com.farhan.urlshortener.web.dto;

import java.time.LocalDateTime;

public record UrlStatisticsResponse(
        String shortCode,
        String longUrl,
        long clickCount,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
