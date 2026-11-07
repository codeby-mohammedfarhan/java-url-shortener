package com.farhan.urlshortener.repository;

import com.farhan.urlshortener.entity.Url;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:url_repository;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UrlRepositoryIntegrationTest {

    @Autowired
    private UrlRepository urlRepository;

    @BeforeEach
    void clearDatabase() {
        urlRepository.deleteAll();
    }

    @Test
    void incrementsClickCountForActiveUrl() {
        Url url = saveUrl("active", LocalDateTime.now().plusDays(1));

        int affectedRows = urlRepository.incrementClickCountIfActive(url.getShortCode());

        assertEquals(1, affectedRows);
        assertEquals(1L, urlRepository.findByShortCode(url.getShortCode()).orElseThrow().getClickCount());
    }

    @Test
    void multipleIncrementsAccumulate() {
        Url url = saveUrl("multiple", LocalDateTime.now().plusDays(1));

        assertEquals(1, urlRepository.incrementClickCountIfActive(url.getShortCode()));
        assertEquals(1, urlRepository.incrementClickCountIfActive(url.getShortCode()));
        assertEquals(1, urlRepository.incrementClickCountIfActive(url.getShortCode()));

        assertEquals(3L, urlRepository.findByShortCode(url.getShortCode()).orElseThrow().getClickCount());
    }

    @Test
    void doesNotIncrementExpiredUrl() {
        Url url = saveUrl("expired", LocalDateTime.now().minusSeconds(10));

        int affectedRows = urlRepository.incrementClickCountIfActive(url.getShortCode());

        assertEquals(0, affectedRows);
        assertEquals(0L, urlRepository.findByShortCode(url.getShortCode()).orElseThrow().getClickCount());
    }

    @Test
    void returnsZeroForUnknownShortCode() {
        assertEquals(0, urlRepository.incrementClickCountIfActive("not-found"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentIncrementsAreAllApplied() throws Exception {
        Url url = saveUrl("concurrent", LocalDateTime.now().plusDays(1));
        int requestCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            List<Future<Integer>> increments = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                increments.add(executor.submit(
                        () -> urlRepository.incrementClickCountIfActive(url.getShortCode())));
            }
            for (Future<Integer> increment : increments) {
                assertEquals(1, increment.get());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(requestCount,
                urlRepository.findByShortCode(url.getShortCode()).orElseThrow().getClickCount());
    }

    private Url saveUrl(String code, LocalDateTime expiresAt) {
        Url url = new Url(code, "https://example.com/" + code);
        url.setCreatedAt(LocalDateTime.now());
        url.setExpiresAt(expiresAt);
        return urlRepository.saveAndFlush(url);
    }
}
