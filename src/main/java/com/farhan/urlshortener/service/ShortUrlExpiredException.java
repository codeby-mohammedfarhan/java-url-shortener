package com.farhan.urlshortener.service;

public class ShortUrlExpiredException extends RuntimeException {

    public ShortUrlExpiredException(String code) {
        super("Short URL has expired: " + code);
    }
}
