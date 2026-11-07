package com.farhan.urlshortener.service;

import com.farhan.urlshortener.cache.UrlCacheService;
import com.farhan.urlshortener.encoder.Base62Encoder;
import com.farhan.urlshortener.entity.Url;
import com.farhan.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    private static final String CODE = "abc123";
    private static final String LONG_URL = "https://example.com/path";

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private Base62Encoder encoder;

    @Mock
    private UrlCacheService urlCacheService;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        service = new UrlShortenerService(urlRepository, encoder, urlCacheService);
    }

    @Test
    void cacheHitIncrementsClicksWithoutLoadingEntity() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);
        when(urlCacheService.get(CODE)).thenReturn(Optional.of(
                new UrlCacheService.CachedUrl(LONG_URL, expiry)));
        when(urlRepository.incrementClickCountIfActive(CODE)).thenReturn(1);

        assertEquals(LONG_URL, service.resolve(CODE));

        verify(urlRepository, never()).findByShortCode(CODE);
        verify(urlRepository, never()).findByShortCodeForUpdate(CODE);
        verify(urlRepository).incrementClickCountIfActive(CODE);
    }

    @Test
    void cacheMissLoadsFromDatabaseAndPopulatesCache() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);
        Url url = url(CODE, LONG_URL, expiry);
        when(urlCacheService.get(CODE)).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode(CODE)).thenReturn(Optional.of(url));
        when(urlRepository.incrementClickCountIfActive(CODE)).thenReturn(1);

        assertEquals(LONG_URL, service.resolve(CODE));

        verify(urlRepository).findByShortCode(CODE);
        verify(urlRepository, never()).findByShortCodeForUpdate(CODE);
        verify(urlCacheService).put(CODE, LONG_URL, expiry);
    }

    @Test
    void expiredCachedEntryIsEvictedAndDatabaseValueIsUsed() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);
        when(urlCacheService.get(CODE)).thenReturn(Optional.of(
                new UrlCacheService.CachedUrl("https://stale.example/", LocalDateTime.now().minusSeconds(1))));
        when(urlRepository.findByShortCode(CODE)).thenReturn(Optional.of(url(CODE, LONG_URL, expiry)));
        when(urlRepository.incrementClickCountIfActive(CODE)).thenReturn(1);

        assertEquals(LONG_URL, service.resolve(CODE));

        verify(urlCacheService).evict(CODE);
        verify(urlCacheService).put(CODE, LONG_URL, expiry);
    }

    @Test
    void staleCacheForDeletedUrlIsEvictedAndReturnsNotFound() {
        when(urlCacheService.get(CODE)).thenReturn(Optional.of(
                new UrlCacheService.CachedUrl(LONG_URL, LocalDateTime.now().plusDays(1))));
        when(urlRepository.incrementClickCountIfActive(CODE)).thenReturn(0);
        when(urlRepository.findByShortCode(CODE)).thenReturn(Optional.empty());

        assertThrows(ShortUrlNotFoundException.class, () -> service.resolve(CODE));

        verify(urlCacheService).evict(CODE);
    }

    @Test
    void expiredDatabaseUrlReturnsGoneAndDoesNotIncrement() {
        when(urlCacheService.get(CODE)).thenReturn(Optional.empty());
        when(urlRepository.findByShortCode(CODE)).thenReturn(Optional.of(
                url(CODE, LONG_URL, LocalDateTime.now().minusSeconds(1))));

        assertThrows(ShortUrlExpiredException.class, () -> service.resolve(CODE));

        verify(urlRepository, never()).incrementClickCountIfActive(CODE);
        verify(urlCacheService).evict(CODE);
    }

    @Test
    void concurrentCacheHitsInvokeAtomicIncrementForEveryRequest() throws Exception {
        int requestCount = 500;
        AtomicLong clickCount = new AtomicLong();
        when(urlCacheService.get(CODE)).thenReturn(Optional.of(
                new UrlCacheService.CachedUrl(LONG_URL, LocalDateTime.now().plusDays(1))));
        when(urlRepository.incrementClickCountIfActive(CODE))
                .thenAnswer(invocation -> {
                    clickCount.incrementAndGet();
                    return 1;
                });

        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            Future<?>[] requests = new Future<?>[requestCount];
            for (int i = 0; i < requestCount; i++) {
                requests[i] = executor.submit(() -> assertEquals(LONG_URL, service.resolve(CODE)));
            }
            for (Future<?> request : requests) {
                request.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(requestCount, clickCount.get());
        verify(urlRepository, never()).findByShortCode(CODE);
    }

    private Url url(String code, String longUrl, LocalDateTime expiresAt) {
        Url url = new Url(code, longUrl);
        url.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        url.setExpiresAt(expiresAt);
        return url;
    }
}