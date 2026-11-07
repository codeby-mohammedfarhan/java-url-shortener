package com.farhan.urlshortener.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @BeforeEach
    void setUp() {
        urlCacheService = new UrlCacheService(redisTemplate);
    }

    @Test
    void getReturnsCachedValueOnCacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(LONG_URL);

        Optional<String> result = urlCacheService.get(SHORT_CODE);

        assertEquals(Optional.of(LONG_URL), result);
        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void getReturnsEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        Optional<String> result = urlCacheService.get(SHORT_CODE);

        assertFalse(result.isPresent());
        verify(valueOperations).get(REDIS_KEY);
    }

    @Test
    void putStoresValueWithPrefixedKeyAndPositiveTtl() {
        Duration ttl = Duration.ofMinutes(15);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        urlCacheService.put(SHORT_CODE, LONG_URL, ttl);

        verify(valueOperations).set(REDIS_KEY, LONG_URL, ttl);
    }

    @Test
    void putDoesNothingWhenTtlIsNull() {
        urlCacheService.put(SHORT_CODE, LONG_URL, null);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void putDoesNothingWhenTtlIsZero() {
        urlCacheService.put(SHORT_CODE, LONG_URL, Duration.ZERO);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void putDoesNothingWhenTtlIsNegative() {
        urlCacheService.put(SHORT_CODE, LONG_URL, Duration.ofSeconds(-1));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void evictDeletesPrefixedKey() {
        urlCacheService.evict(SHORT_CODE);

        verify(redisTemplate).delete(REDIS_KEY);
    }
}
