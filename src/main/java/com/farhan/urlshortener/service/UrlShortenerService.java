package com.farhan.urlshortener.service;

import com.farhan.urlshortener.encoder.Base62Encoder;
import com.farhan.urlshortener.entity.Url;
import com.farhan.urlshortener.repository.UrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UrlShortenerService {

    private final UrlRepository urlRepository;
    private final Base62Encoder encoder;

    public UrlShortenerService(UrlRepository urlRepository, Base62Encoder encoder) {
        this.urlRepository = urlRepository;
        this.encoder = encoder;
    }

    @Transactional
    public String shorten(String originalUrl) {
        // MySQL needs a row before it can generate the identity ID. The marker is
        // unique, fits the column, and cannot collide with Base62 output.
        String temporaryCode = "!" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        Url url = urlRepository.saveAndFlush(new Url(temporaryCode, originalUrl));

        String code = encoder.encode(url.getId());
        url.setShortCode(code);
        urlRepository.saveAndFlush(url);
        return code;
    }

    @Transactional(readOnly = true)
    public String resolve(String code) {
        return urlRepository.findByShortCode(code)
                .map(Url::getLongUrl)
                .orElseThrow(() -> new ShortUrlNotFoundException(code));
    }
}
