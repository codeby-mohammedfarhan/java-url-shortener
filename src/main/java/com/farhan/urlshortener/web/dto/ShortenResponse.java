package com.farhan.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Created short URL")
public record ShortenResponse(
        @Schema(description = "Generated short code", example = "aZ19xQ") String shortCode,
        @Schema(description = "Absolute short URL that redirects to the destination", example = "http://localhost:8080/aZ19xQ", format = "uri") String shortUrl) {
}
