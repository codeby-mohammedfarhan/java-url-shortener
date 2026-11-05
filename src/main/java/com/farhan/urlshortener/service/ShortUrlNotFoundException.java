package com.farhan.urlshortener.service;

public class ShortUrlNotFoundException extends RuntimeException {

    public ShortUrlNotFoundException(String code) {
        super("No URL is registered for code: " + code);
    }
}
