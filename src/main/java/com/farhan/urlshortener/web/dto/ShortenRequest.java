package com.farhan.urlshortener.web.dto;

import com.farhan.urlshortener.validation.ValidHttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Details for the URL to shorten")
public record ShortenRequest(
        @Schema(description = "Absolute destination URL; only HTTP and HTTPS URLs are accepted", example = "https://example.com/articles/spring", format = "uri", requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1)
        @NotBlank
        @ValidHttpUrl
        String longUrl
) {
}
