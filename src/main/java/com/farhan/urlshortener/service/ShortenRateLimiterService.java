package com.farhan.urlshortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShortenRateLimiterService {

    private static final String KEY_PREFIX = "rate-limit:shorten:";
    private static final DefaultRedisScript<Long> INCREMENT_AND_SET_EXPIRY_SCRIPT =
            new DefaultRedisScript<>(
                    "local count = redis.call('INCR', KEYS[1]) " +
                            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                            "return count",
                    Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maximumRequests;
    private final long windowSeconds;

    public ShortenRateLimiterService(
            StringRedisTemplate redisTemplate,
            @Value("${app.rate-limit.shorten.max-requests:10}") int maximumRequests,
            @Value("${app.rate-limit.shorten.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.maximumRequests = maximumRequests;
        this.windowSeconds = windowSeconds;
    }

    public boolean isAllowed(String clientIp) {
        String key = KEY_PREFIX + clientIp;
        Long requestCount = redisTemplate.execute(
                INCREMENT_AND_SET_EXPIRY_SCRIPT,
                List.of(key),
                Long.toString(windowSeconds));

        return requestCount != null && requestCount <= maximumRequests;
    }
}
