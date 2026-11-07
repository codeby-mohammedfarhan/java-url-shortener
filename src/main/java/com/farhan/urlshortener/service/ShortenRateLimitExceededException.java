package com.farhan.urlshortener.service;

public class ShortenRateLimitExceededException extends RuntimeException {

    public ShortenRateLimitExceededException() {
        super("URL creation rate limit exceeded");
    }
}
