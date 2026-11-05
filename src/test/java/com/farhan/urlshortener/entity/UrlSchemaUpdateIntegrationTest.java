package com.farhan.urlshortener.entity;

import com.farhan.urlshortener.repository.UrlRepository;
import com.farhan.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:legacy_url_schema;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:legacy-url-schema.sql",
        "spring.jpa.hibernate.ddl-auto=update"
})
class UrlSchemaUpdateIntegrationTest {

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Test
    void updateAddsExpiryColumnAndPreservesExistingRows() {
        Url existing = urlRepository.findByShortCode("legacy1").orElseThrow();

        assertTrue(urlRepository.existsById(existing.getId()));
        assertNotNull(urlShortenerService.getStatistics("legacy1").expiresAt());

        String code = urlShortenerService.shorten("https://example.com/new");
        Url inserted = urlRepository.findByShortCode(code).orElseThrow();
        assertNotNull(inserted.getExpiresAt());
        assertEquals(0L, inserted.getClickCount());
    }
}
