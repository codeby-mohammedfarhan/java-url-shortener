package com.farhan.urlshortener.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    private static final String SHORT_CODE = "abc123";
    private static final String LONG_URL = "https://example.com/path";
    private static final String REDIS_KEY = "url:" + SHORT_CODE;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UrlCacheService urlCacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        urlCacheService = new UrlCacheService(redisTemplate, objectMapper);
    }

    @Test
    void getDeserializesUrlAndAbsoluteExpiry() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(2);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(
                new UrlCacheService.CachedUrl(LONG_URL, expiresAt)));

        Optional<UrlCacheService.CachedUrl> result = urlCacheService.get(SHORT_CODE);

        assertEquals(Optional.of(new UrlCacheService.CachedUrl(LONG_URL, expiresAt)), result);
        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        assertFalse(urlCacheService.get(SHORT_CODE).isPresent());
        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void putSerializesMetadataAndUsesTtlUntilAbsoluteExpiry() throws Exception {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(20);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        urlCacheService.put(SHORT_CODE, LONG_URL, expiresAt);

        org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Duration> ttlCaptor = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(REDIS_KEY), valueCaptor.capture(), ttlCaptor.capture());
        UrlCacheService.CachedUrl cached = objectMapper.readValue(
                valueCaptor.getValue(), UrlCacheService.CachedUrl.class);
        assertEquals(LONG_URL, cached.longUrl());
        assertEquals(expiresAt, cached.expiresAt());
        assertTrueWithin(ttlCaptor.getValue(), Duration.ofMinutes(20));
    }

    @Test
    void putDoesNotCacheNullOrExpiredValues() {
        urlCacheService.put(SHORT_CODE, null, LocalDateTime.now().plusMinutes(1));
        urlCacheService.put(SHORT_CODE, LONG_URL, LocalDateTime.now().minusSeconds(1));
        urlCacheService.put(SHORT_CODE, LONG_URL, null);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void getEvictsExpiredMetadata() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(
                new UrlCacheService.CachedUrl(LONG_URL, LocalDateTime.now().minusSeconds(1))));

        assertFalse(urlCacheService.get(SHORT_CODE).isPresent());
        verify(redisTemplate).delete(REDIS_KEY);
    }

    @Test
    void getEvictsMalformedCachedData() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn("{not-json");

        assertFalse(urlCacheService.get(SHORT_CODE).isPresent());
        verify(redisTemplate).delete(REDIS_KEY);
    }

    @Test
    void evictDeletesPrefixedKey() {
        urlCacheService.evict(SHORT_CODE);
        verify(redisTemplate).delete(REDIS_KEY);
    }

    private void assertTrueWithin(Duration actual, Duration expected) {
        if (actual.isNegative() || actual.isZero() || actual.compareTo(expected) > 0
                || expected.minus(actual).compareTo(Duration.ofSeconds(2)) > 0) {
            throw new AssertionError("TTL was not close to expiry duration: " + actual);
        }
    }
}