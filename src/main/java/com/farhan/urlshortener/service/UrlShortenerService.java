package com.farhan.urlshortener.service;

import com.farhan.urlshortener.encoder.Base62Encoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class UrlShortenerService {

    private final IdGenerator idGenerator;
    private final Base62Encoder encoder;
    private final ConcurrentHashMap<String, String> urlsByCode = new ConcurrentHashMap<>();

    public UrlShortenerService(IdGenerator idGenerator, Base62Encoder encoder) {
        this.idGenerator = idGenerator;
        this.encoder = encoder;
    }

    public String shorten(String originalUrl) {
        long id = idGenerator.nextId();
        String code = encoder.encode(id);
        urlsByCode.put(code, originalUrl);
        return code;
    }

    public String resolve(String code) {
        String originalUrl = urlsByCode.get(code);
        if (originalUrl == null) {
            throw new ShortUrlNotFoundException(code);
        }
        return originalUrl;
    }
}
