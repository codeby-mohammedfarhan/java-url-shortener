package com.farhan.urlshortener.web;

import com.farhan.urlshortener.service.ShortUrlNotFoundException;
import com.farhan.urlshortener.service.ShortenRateLimiterService;
import com.farhan.urlshortener.service.UrlStatistics;
import com.farhan.urlshortener.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShortenerController.class)
class ShortenerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlShortenerService urlShortenerService;

    @MockBean
    private ShortenRateLimiterService shortenRateLimiterService;

    @Test
    void postShortenReturnsCodeAndShortUrl() throws Exception {
        when(shortenRateLimiterService.isAllowed("127.0.0.1")).thenReturn(true);
        when(urlShortenerService.shorten("https://example.com/path")).thenReturn("abc");

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://example.com/path\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/abc"));

        verify(urlShortenerService).shorten("https://example.com/path");
        verify(shortenRateLimiterService).isAllowed("127.0.0.1");
    }

    @Test
    void postShortenReturns429WithApiErrorWhenLimitIsExceeded() throws Exception {
        when(shortenRateLimiterService.isAllowed("127.0.0.1")).thenReturn(false);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"https://example.com/path\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("URL creation rate limit exceeded"));

        verifyNoInteractions(urlShortenerService);
    }

    @Test
    void postShortenRejectsNonHttpSchemesWithJson400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"longUrl\":\"ftp://example.com/file\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void postShortenReturnsJson400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request body must contain valid JSON"));
    }

    @Test
    void getCodeReturnsHttp302Redirect() throws Exception {
        when(urlShortenerService.resolve("abc")).thenReturn("https://example.com/path");

        mockMvc.perform(get("/abc"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/path"));

        verifyNoInteractions(shortenRateLimiterService);
    }

    @Test
    void statisticsEndpointIsNotRateLimited() throws Exception {
        LocalDateTime createdAt = LocalDateTime.now();
        when(urlShortenerService.getStatistics("abc"))
                .thenReturn(new UrlStatistics("abc", "https://example.com/path", 0L,
                        createdAt, createdAt.plusDays(30)));

        mockMvc.perform(get("/stats/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc"));

        verifyNoInteractions(shortenRateLimiterService);
    }

    @Test
    void unknownCodeReturnsJson404() throws Exception {
        doThrow(new ShortUrlNotFoundException("missing"))
                .when(urlShortenerService).resolve("missing");

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
