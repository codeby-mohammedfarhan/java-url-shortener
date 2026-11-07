package com.farhan.urlshortener.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public UrlCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public UrlCacheService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, new ObjectMapper().findAndRegisterModules());
    }

    public Optional<CachedUrl> get(String shortCode) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + shortCode);
        if (value == null) {
            return Optional.empty();
        }

        try {
            CachedUrl cachedUrl = objectMapper.readValue(value, CachedUrl.class);
            if (cachedUrl.longUrl() == null || cachedUrl.longUrl().isBlank()
                    || cachedUrl.expiresAt() == null
                    || !cachedUrl.expiresAt().isAfter(LocalDateTime.now())) {
                evict(shortCode);
                return Optional.empty();
            }
            return Optional.of(cachedUrl);
        } catch (JsonProcessingException | RuntimeException exception) {
            evict(shortCode);
            return Optional.empty();
        }
    }

    public void put(String shortCode, String longUrl, LocalDateTime expiresAt) {
        if (longUrl == null || longUrl.isBlank() || expiresAt == null) {
            return;
        }

        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        try {
            String value = objectMapper.writeValueAsString(new CachedUrl(longUrl, expiresAt));
            redisTemplate.opsForValue().set(KEY_PREFIX + shortCode, value, ttl);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize URL cache entry", exception);
        }
    }

    public void evict(String shortCode) {
        redisTemplate.delete(KEY_PREFIX + shortCode);
    }

    public record CachedUrl(String longUrl, LocalDateTime expiresAt) { }
}
