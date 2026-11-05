package com.farhan.urlshortener.web;

import com.farhan.urlshortener.service.UrlShortenerService;
import com.farhan.urlshortener.web.dto.ShortenRequest;
import com.farhan.urlshortener.web.dto.ShortenResponse;
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
public class ShortenerController {

    private final UrlShortenerService urlShortenerService;

    public ShortenerController(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request) {
        String code = urlShortenerService.shorten(request.longUrl());
        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(code)
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(new ShortenResponse(code, shortUrl));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String destination = urlShortenerService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destination)).build();
    }
}
