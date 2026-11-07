
package com.farhan.urlshortener.service;

import com.farhan.urlshortener.cache.UrlCacheService;
import com.farhan.urlshortener.encoder.Base62Encoder;
import com.farhan.urlshortener.entity.Url;
import com.farhan.urlshortener.repository.UrlRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UrlShortenerService {

    private final UrlRepository urlRepository;
    private final Base62Encoder encoder;
    private final UrlCacheService urlCacheService;

    @Value("${app.url.expiry:30d}")
    private Duration expiryDuration = Duration.ofDays(30);

    public UrlShortenerService(
            UrlRepository urlRepository,
            Base62Encoder encoder,
            UrlCacheService urlCacheService) {

        this.urlRepository = urlRepository;
        this.encoder = encoder;
        this.urlCacheService = urlCacheService;
    }

    @Transactional
    public String shorten(String originalUrl) {

        String temporaryCode = "!" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 15);

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

        Optional<UrlCacheService.CachedUrl> cached = urlCacheService.get(code);
        if (cached.isPresent()) {
            UrlCacheService.CachedUrl cachedUrl = cached.get();
            if (cachedUrl.expiresAt().isAfter(LocalDateTime.now())) {
                if (urlRepository.incrementClickCountIfActive(code) == 1) {
                    return cachedUrl.longUrl();
                }

                urlCacheService.evict(code);
                throwNotFoundOrExpired(code);
            }

            // Redis may have retained a stale entry; reload authoritative data.
            urlCacheService.evict(code);
        }

        Url url = urlRepository.findByShortCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        LocalDateTime expiresAt = ensureNotExpired(url);
        String longUrl = url.getLongUrl();

        if (urlRepository.incrementClickCountIfActive(code) != 1) {
            urlCacheService.evict(code);
            throwNotFoundOrExpired(code);
        }

        urlCacheService.put(code, longUrl, expiresAt);
        return longUrl;
    }

    @Transactional
    public UrlStatistics getStatistics(String code) {

        Url url = urlRepository.findByShortCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));

        LocalDateTime expiresAt = ensureNotExpired(url);

        return new UrlStatistics(
                url.getShortCode(),
                url.getLongUrl(),
                url.getClickCount(),
                url.getCreatedAt(),
                expiresAt
        );
    }

    @Transactional
    public long deleteExpiredUrls() {

        LocalDateTime now = LocalDateTime.now();

        return urlRepository.deleteExpiredUrls(
                now,
                now.minus(expiryDuration)
        );
    }

    private LocalDateTime ensureNotExpired(Url url) {

        LocalDateTime expiresAt = url.getExpiresAt();

        if (expiresAt == null) {
            expiresAt = url.getCreatedAt().plus(expiryDuration);
            url.setExpiresAt(expiresAt);
        }

        if (!expiresAt.isAfter(LocalDateTime.now())) {
            urlCacheService.evict(url.getShortCode());
            throw new ShortUrlExpiredException(url.getShortCode());
        }

        return expiresAt;
    }

    private void throwNotFoundOrExpired(String code) {
        Url current = urlRepository.findByShortCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
        ensureNotExpired(current);
        // The conditional update rejected the row between lookup and update.
        throw new ShortUrlExpiredException(code);
    }
}
