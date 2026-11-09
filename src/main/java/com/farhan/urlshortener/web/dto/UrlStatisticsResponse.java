package com.farhan.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Usage and lifecycle information for a shortened URL")
public record UrlStatisticsResponse(
        @Schema(description = "Short code", example = "aZ19xQ")
        String shortCode,
        @Schema(description = "Original destination URL", example = "https://example.com/articles/spring", format = "uri")
        String longUrl,
        @Schema(description = "Number of successful redirects", example = "42", minimum = "0")
        long clickCount,
        @Schema(description = "Creation timestamp in ISO-8601 local date-time format", example = "2026-11-09T10:15:30")
        LocalDateTime createdAt,
        @Schema(description = "Expiration timestamp in ISO-8601 local date-time format", example = "2026-12-09T10:15:30", nullable = true)
        LocalDateTime expiresAt
) {
}
