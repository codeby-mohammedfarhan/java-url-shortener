package com.farhan.urlshortener.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredUrlCleanupJob {

    private final UrlShortenerService urlShortenerService;

    public ExpiredUrlCleanupJob(UrlShortenerService urlShortenerService) {
        this.urlShortenerService = urlShortenerService;
    }

    @Scheduled(cron = "${app.url.cleanup-cron:0 0 0 * * *}")
    public void cleanupExpiredUrls() {
        urlShortenerService.deleteExpiredUrls();
    }
}
