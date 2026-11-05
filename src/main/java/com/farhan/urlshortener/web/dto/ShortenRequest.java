package com.farhan.urlshortener.web.dto;

import com.farhan.urlshortener.validation.ValidHttpUrl;
import jakarta.validation.constraints.NotBlank;

public record ShortenRequest(
        @NotBlank
        @ValidHttpUrl
        String longUrl
) {
}
