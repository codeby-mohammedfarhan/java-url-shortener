package com.farhan.urlshortener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.farhan.urlshortener.encoder.Base62Encoder;
import com.farhan.urlshortener.entity.Url;
import com.farhan.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class UrlShortenerServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlShortenerService urlShortenerService;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private Base62Encoder base62Encoder;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearDatabase() {
        urlRepository.deleteAll();
    }

    @Test
    void shortensValidUrlAndPersistsGeneratedIdAndCode() throws Exception {
        String longUrl = "https://example.com/path";

        String code = shortenThroughApi(longUrl);

        Url persisted = urlRepository.findByShortCode(code).orElseThrow();
        assertEquals(longUrl, persisted.getLongUrl());
        assertEquals(base62Encoder.encode(persisted.getId()), persisted.getShortCode());
        assertTrue(persisted.getId() > 0);
    }

    @Test
    void redirectsExistingShortCode() throws Exception {
        String longUrl = "https://example.com/redirect";
        String code = urlShortenerService.shorten(longUrl);

        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
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
    void duplicateUrlsProduceDifferentCodes() throws Exception {
        String longUrl = "https://example.com/duplicate";

        String firstCode = shortenThroughApi(longUrl);
        String secondCode = shortenThroughApi(longUrl);

        assertNotEquals(firstCode, secondCode);
        assertEquals(2, urlRepository.count());
    }

    @Test
    void persistedUrlCanBeResolvedByANewServiceInstance() {
        String longUrl = "https://example.com/persisted";
        String code = urlShortenerService.shorten(longUrl);

        UrlShortenerService restartedService = new UrlShortenerService(urlRepository, base62Encoder);

        assertEquals(longUrl, restartedService.resolve(code));
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
