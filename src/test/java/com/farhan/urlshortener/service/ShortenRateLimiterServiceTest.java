package com.farhan.urlshortener.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortenRateLimiterServiceTest {

    private static final String IP_ADDRESS = "192.0.2.10";
    private static final String REDIS_KEY = "rate-limit:shorten:" + IP_ADDRESS;
    private static final long WINDOW_SECONDS = 60;

    private StringRedisTemplate redisTemplate;
    private ShortenRateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        rateLimiterService = new ShortenRateLimiterService(redisTemplate, 3, WINDOW_SECONDS);
    }

    @Test
    void allowsFirstRequestFromIp() {
        stubRedisCount(1L);

        assertTrue(rateLimiterService.isAllowed(IP_ADDRESS));
    }

    @Test
    void allowsRequestsWithinConfiguredLimit() {
        stubRedisCount(2L);

        assertTrue(rateLimiterService.isAllowed(IP_ADDRESS));
    }

    @Test
    void rejectsRequestExceedingConfiguredLimit() {
        stubRedisCount(4L);

        assertFalse(rateLimiterService.isAllowed(IP_ADDRESS));
    }

    @Test
    void usesDifferentRedisKeysForDifferentIpAddresses() {
        stubRedisCount(1L);

        rateLimiterService.isAllowed("192.0.2.10");
        rateLimiterService.isAllowed("192.0.2.11");

        verifyRedisScriptCalledWithKey("rate-limit:shorten:192.0.2.10");
        verifyRedisScriptCalledWithKey("rate-limit:shorten:192.0.2.11");
    }

    @Test
    void executesLuaScriptWithExpectedKeyAndWindowDuration() {
        stubRedisCount(1L);

        rateLimiterService.isAllowed(IP_ADDRESS);

        
        verify(redisTemplate).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(REDIS_KEY)),
                org.mockito.ArgumentMatchers.eq(Long.toString(WINDOW_SECONDS)));
    }

    @Test
    void safelyRejectsWhenRedisReturnsNull() {
        stubRedisCount(null);

        assertFalse(rateLimiterService.isAllowed(IP_ADDRESS));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubRedisCount(Long count) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(count);
    }

    private void verifyRedisScriptCalledWithKey(String key) {
        verify(redisTemplate).execute(
                any(RedisScript.class),
                org.mockito.ArgumentMatchers.eq(List.of(key)),
                org.mockito.ArgumentMatchers.eq(Long.toString(WINDOW_SECONDS)));
    }
}
