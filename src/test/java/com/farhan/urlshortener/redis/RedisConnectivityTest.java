package com.farhan.urlshortener.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:redis_connectivity;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RedisConnectivityTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void redisRespondsToPing() {
        String response = stringRedisTemplate.execute(
                (RedisCallback<String>) connection -> connection.ping());

        assertEquals("PONG", response);
    }
}
