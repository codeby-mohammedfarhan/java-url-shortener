package com.farhan.urlshortener.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UrlTest {

    @Test
    void assignsCreatedAtBeforeFirstPersistence() {
        Url url = new Url("1", "https://example.com");

        url.setCreatedAtIfMissing();

        assertNotNull(url.getCreatedAt());
    }

    @Test
    void preservesAnExistingCreatedAt() {
        Url url = new Url("1", "https://example.com");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        url.setCreatedAt(createdAt);

        url.setCreatedAtIfMissing();

        assertEquals(createdAt, url.getCreatedAt());
    }
}
