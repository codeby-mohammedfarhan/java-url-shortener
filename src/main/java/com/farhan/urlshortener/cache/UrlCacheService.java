
package com.farhan.urlshortener.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";

    private final StringRedisTemplate redisTemplate;

    public UrlCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> get(String shortCode) {
        String value = redisTemplate.opsForValue()
                .get(KEY_PREFIX + shortCode);

        return Optional.ofNullable(value);
    }

    public void put(String shortCode, String longUrl, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }

        redisTemplate.opsForValue()
                .set(KEY_PREFIX + shortCode, longUrl, ttl);
    }

    public void evict(String shortCode) {
        redisTemplate.delete(KEY_PREFIX + shortCode);
    }
}
