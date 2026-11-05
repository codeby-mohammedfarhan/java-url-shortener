package com.farhan.urlshortener.service;

import com.farhan.urlshortener.encoder.Base62Encoder;
import com.farhan.urlshortener.entity.Url;
import com.farhan.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
public class UrlShortenerService {

    private final UrlRepository urlRepository;
    private final Base62Encoder encoder;
    @Value("${app.url.expiry:30d}")
    private Duration expiryDuration = Duration.ofDays(30);

    public UrlShortenerService(UrlRepository urlRepository, Base62Encoder encoder) {
        this.urlRepository = urlRepository;
        this.encoder = encoder;
    }

    @Transactional
    public String shorten(String originalUrl) {
        // MySQL needs a row before it can generate the identity ID. The marker is
        // unique, fits the column, and cannot collide with Base62 output.
        String temporaryCode = "!" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        LocalDateTime createdAt = LocalDateTime.now();
        Url newUrl = new Url(temporaryCode, originalUrl);
        newUrl.setCreatedAt(createdAt);
        newUrl.setExpiresAt(createdAt.plus(expiryDuration));
        Url url = urlRepository.saveAndFlush(newUrl);

        String code = encoder.encode(url.getId());
        url.setShortCode(code);
        urlRepository.saveAndFlush(url);
        return code;
    }

    @Transactional
    public String resolve(String code) {
        Url url = urlRepository.findByShortCodeForUpdate(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        ensureNotExpired(url);
        url.setClickCount(url.getClickCount() + 1);
        urlRepository.save(url);
        return url.getLongUrl();
    }

    @Transactional
    public UrlStatistics getStatistics(String code) {
        Url url = urlRepository.findByShortCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        LocalDateTime expiresAt = ensureNotExpired(url);
        return new UrlStatistics(url.getShortCode(), url.getLongUrl(), url.getClickCount(),
                url.getCreatedAt(), expiresAt);
    }

    @Transactional
    public long deleteExpiredUrls() {
        LocalDateTime now = LocalDateTime.now();
        return urlRepository.deleteExpiredUrls(now, now.minus(expiryDuration));
    }

    private LocalDateTime ensureNotExpired(Url url) {
        LocalDateTime expiresAt = url.getExpiresAt();
        if (expiresAt == null) {
            // Rows created before the expiry column was introduced retain their
            // existing createdAt and get the same default lifetime as new links.
            expiresAt = url.getCreatedAt().plus(expiryDuration);
            url.setExpiresAt(expiresAt);
        }
        if (!expiresAt.isAfter(LocalDateTime.now())) {
            throw new ShortUrlExpiredException(url.getShortCode());
        }
        return expiresAt;
    }
}
