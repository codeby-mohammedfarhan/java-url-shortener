package com.farhan.urlshortener.service;

import com.farhan.urlshortener.encoder.Base62Encoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlShortenerServiceTest {

    @Test
    void duplicateUrlsReceiveDifferentCodesAndResolveIndependently() {
        UrlShortenerService service = new UrlShortenerService(new IdGenerator(), new Base62Encoder());
        String originalUrl = "https://example.com/path";

        String firstCode = service.shorten(originalUrl);
        String secondCode = service.shorten(originalUrl);

        assertEquals("1", firstCode);
        assertEquals("2", secondCode);
        assertEquals(originalUrl, service.resolve(firstCode));
        assertEquals(originalUrl, service.resolve(secondCode));
    }

    @Test
    void unknownCodeThrowsNotFoundException() {
        UrlShortenerService service = new UrlShortenerService(new IdGenerator(), new Base62Encoder());

        assertThrows(ShortUrlNotFoundException.class, () -> service.resolve("missing"));
    }
}
