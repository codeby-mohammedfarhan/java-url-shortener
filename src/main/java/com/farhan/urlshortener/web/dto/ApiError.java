package com.farhan.urlshortener.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard error response returned when an API request cannot be completed")
public record ApiError(
        @Schema(description = "HTTP status code", example = "400") int status,
        @Schema(description = "HTTP status reason", example = "Bad Request") String error,
        @Schema(description = "Details about the error", example = "longUrl must be a valid HTTP or HTTPS URL") String message) {
}
