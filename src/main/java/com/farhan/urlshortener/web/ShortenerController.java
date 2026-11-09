package com.farhan.urlshortener.web;

import com.farhan.urlshortener.service.ShortenRateLimitExceededException;
import com.farhan.urlshortener.service.ShortenRateLimiterService;
import com.farhan.urlshortener.service.UrlShortenerService;
import com.farhan.urlshortener.web.dto.ShortenRequest;
import com.farhan.urlshortener.web.dto.ShortenResponse;
import com.farhan.urlshortener.web.dto.UrlStatisticsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@Tag(name = "URL Shortener", description = "Create short links, redirect to destinations, and inspect link statistics.")
public class ShortenerController {

    private final UrlShortenerService urlShortenerService;
    private final ShortenRateLimiterService shortenRateLimiterService;

    public ShortenerController(
            UrlShortenerService urlShortenerService,
            ShortenRateLimiterService shortenRateLimiterService) {
        this.urlShortenerService = urlShortenerService;
        this.shortenRateLimiterService = shortenRateLimiterService;
    }

    @PostMapping("/shorten")
    @Operation(summary = "Create a short URL", description = "Creates a short link for a valid HTTP or HTTPS URL. Requests are rate limited per client IP.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short URL created",
                    content = @Content(schema = @Schema(implementation = ShortenResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid URL or malformed request",
                    content = @Content(schema = @Schema(implementation = com.farhan.urlshortener.web.dto.ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Client exceeded the shorten rate limit",
                    content = @Content(schema = @Schema(implementation = com.farhan.urlshortener.web.dto.ApiError.class)))
    })
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest.getRemoteAddr();
        if (!shortenRateLimiterService.isAllowed(clientIp)) {
            throw new ShortenRateLimitExceededException();
        }

        String code = urlShortenerService.shorten(request.longUrl());
        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(code)
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ShortenResponse(code, shortUrl));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Redirect to the original URL", description = "Looks up a short code and responds with HTTP 302 and a Location header pointing to its destination.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the original URL",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", description = "Original destination URL", schema = @Schema(type = "string", format = "uri"))),
            @ApiResponse(responseCode = "404", description = "Short code does not exist",
                    content = @Content(schema = @Schema(implementation = com.farhan.urlshortener.web.dto.ApiError.class))),
            @ApiResponse(responseCode = "410", description = "Short URL has expired",
                    content = @Content(schema = @Schema(implementation = com.farhan.urlshortener.web.dto.ApiError.class)))
    })
    public ResponseEntity<Void> redirect(
            @Parameter(description = "Short code from a previously created URL", example = "aZ19xQ", required = true)
            @PathVariable String code) {
        String destination = urlShortenerService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination)).build();
    }

    @GetMapping("/stats/{code}")
    @Operation(summary = "Get URL statistics", description = "Returns destination, click count, and creation and expiration timestamps for a short code.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL statistics retrieved",
                    content = @Content(schema = @Schema(implementation = UrlStatisticsResponse.class))),
            @ApiResponse(responseCode = "404", description = "Short code does not exist",
                    content = @Content(schema = @Schema(implementation = com.farhan.urlshortener.web.dto.ApiError.class))),
            @ApiResponse(responseCode = "410", description = "Short URL has expired",
                    content = @Content(schema = @Schema(implementation = com.farhan.urlshortener.web.dto.ApiError.class)))
    })
    public UrlStatisticsResponse statistics(
            @Parameter(description = "Short code to inspect", example = "aZ19xQ", required = true)
            @PathVariable String code) {
        var statistics = urlShortenerService.getStatistics(code);
        return new UrlStatisticsResponse(statistics.shortCode(), statistics.longUrl(),
                statistics.clickCount(), statistics.createdAt(), statistics.expiresAt());
    }
}
