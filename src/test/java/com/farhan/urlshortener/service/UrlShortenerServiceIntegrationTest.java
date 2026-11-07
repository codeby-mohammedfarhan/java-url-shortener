package com.farhan.urlshortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farhan.urlshortener.encoder.Base62Encoder;
import com.farhan.urlshortener.entity.Url;
import com.farhan.urlshortener.repository.UrlRepository;
import com.farhan.urlshortener.service.ExpiredUrlCleanupJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.farhan.urlshortener.cache.UrlCacheService;

import java.util.Map;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:urlshortener;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "app.url.expiry=30d",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class UrlShortenerServiceIntegrationTest {

    @MockBean
    private UrlCacheService urlCacheService;

    @MockBean
    private ShortenRateLimiterService shortenRateLimiterService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExpiredUrlCleanupJob expiredUrlCleanupJob;

    @Autowired
    private Base62Encoder base62Encoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        urlRepository.deleteAll();
        when(urlCacheService.get(anyString())).thenReturn(Optional.empty());
        when(shortenRateLimiterService.isAllowed(anyString())).thenReturn(true);
    }

    @Test
    void shortensValidUrlAndPersistsGeneratedIdAndCode() throws Exception {
        String longUrl = "https://example.com/path";

        String code = shortenThroughApi(longUrl);

        Url persisted = urlRepository.findByShortCode(code).orElseThrow();
        assertEquals(longUrl, persisted.getLongUrl());
        assertEquals(base62Encoder.encode(persisted.getId()), persisted.getShortCode());
        assertTrue(persisted.getId() > 0);
        assertEquals(0L, persisted.getClickCount());
        assertEquals(persisted.getCreatedAt().plusDays(30), persisted.getExpiresAt());
    }

    @Test
    void repositoryInsertDefaultsClickCountAndPopulatesExpiry() {
        Url url = new Url("direct-insert", "https://example.com/direct");

        Url persisted = urlRepository.saveAndFlush(url);

        assertEquals(0L, persisted.getClickCount());
        assertNotNull(persisted.getCreatedAt());
        assertEquals(persisted.getCreatedAt().plusDays(30), persisted.getExpiresAt());
    }

    @Test
    void redirectsExistingShortCode() throws Exception {
        String longUrl = "https://example.com/redirect";
        String code = urlShortenerService.shorten(longUrl);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));

        assertEquals(1L, urlRepository.findByShortCode(code).orElseThrow().getClickCount());
    }

    @Test
    void multipleRedirectsIncrementClickCount() throws Exception {
        String code = urlShortenerService.shorten("https://example.com/multiple");

        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());
        mockMvc.perform(get("/{code}", code)).andExpect(status().isFound());

        assertEquals(3L, urlRepository.findByShortCode(code).orElseThrow().getClickCount());
    }

    @Test
    void expiredShortCodeReturns410WithoutIncrementingClickCount() throws Exception {
        Url expired = saveExpiredUrl("expired-redirect");

        mockMvc.perform(get("/{code}", expired.getShortCode()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.error").value("Gone"));

        assertEquals(0L, urlRepository.findByShortCode(expired.getShortCode()).orElseThrow().getClickCount());
    }

    @Test
    void returnsJson404ForUnknownShortCode() throws Exception {
        mockMvc.perform(get("/unknown-code"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void rejectsInvalidUrlWithJson400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("longUrl", "ftp://example.com/file"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void statisticsReturnsCurrentUrlDetails() throws Exception {
        String longUrl = "https://example.com/stats";
        String code = urlShortenerService.shorten(longUrl);

        mockMvc.perform(get("/stats/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(code))
                .andExpect(jsonPath("$.longUrl").value(longUrl))
                .andExpect(jsonPath("$.clickCount").value(0))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void statisticsReturns404ForUnknownCode() throws Exception {
        mockMvc.perform(get("/stats/unknown-stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void statisticsReturns410ForExpiredCode() throws Exception {
        Url expired = saveExpiredUrl("expired-stats");

        mockMvc.perform(get("/stats/{code}", expired.getShortCode()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.status").value(410));
    }

    @Test
    void duplicateUrlsProduceDifferentCodes() throws Exception {
        String longUrl = "https://example.com/duplicate";

        String firstCode = shortenThroughApi(longUrl);
        String secondCode = shortenThroughApi(longUrl);

        assertNotEquals(firstCode, secondCode);
        assertEquals(2, urlRepository.count());
    }

    @Test
    @Transactional
    void persistedUrlCanBeResolvedByANewServiceInstance() {
        String longUrl = "https://example.com/persisted";
        String code = urlShortenerService.shorten(longUrl);

        UrlShortenerService restartedService = new UrlShortenerService(urlRepository, base62Encoder, urlCacheService);

        assertEquals(longUrl, restartedService.resolve(code));
    }

    @Test
    void cleanupJobDeletesExpiredUrlsAndKeepsActiveUrls() {
        Url expired = saveExpiredUrl("expired-cleanup");
        String activeCode = urlShortenerService.shorten("https://example.com/active");
        String legacyCode = "legacy-cleanup";
        jdbcTemplate.update(
                "insert into urls (created_at, long_url, short_code, click_count, expires_at) "
                        + "values (?, ?, ?, ?, null)",
                Timestamp.valueOf(LocalDateTime.now().minusDays(31)),
                "https://example.com/legacy-cleanup", legacyCode, 0L);

        expiredUrlCleanupJob.cleanupExpiredUrls();

        assertTrue(urlRepository.findByShortCode(expired.getShortCode()).isEmpty());
        assertTrue(urlRepository.findByShortCode(activeCode).isPresent());
        assertTrue(urlRepository.findByShortCode(legacyCode).isEmpty());
    }

    private Url saveExpiredUrl(String code) {
        LocalDateTime createdAt = LocalDateTime.now().minusDays(31);
        Url expired = new Url(code, "https://example.com/" + code);
        expired.setCreatedAt(createdAt);
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        return urlRepository.saveAndFlush(expired);
    }

    private String shortenThroughApi(String longUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("longUrl", longUrl))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("shortCode").asText();
    }
}
