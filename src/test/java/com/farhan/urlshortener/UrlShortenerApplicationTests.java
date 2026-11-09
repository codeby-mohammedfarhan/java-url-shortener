package com.farhan.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.OK;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:urlshortener;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UrlShortenerApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void openApiDocumentDescribesEndpointsAndUsesLocalHttpWithoutForwardedHeaders() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertEquals(OK, response.getStatusCode());
        assertEquals("URL Shortener API", JsonPath.read(response.getBody(), "$.info.title"));
        assertEquals("1.0.0", JsonPath.read(response.getBody(), "$.info.version"));
        assertTrue(JsonPath.read(response.getBody(), "$.paths['/shorten'].post.responses['201']") != null);
        assertTrue(JsonPath.read(response.getBody(), "$.paths['/{code}'].get.responses['302']") != null);
        assertTrue(JsonPath.read(response.getBody(), "$.paths['/stats/{code}'].get.responses['200']") != null);
        String serverUrl = JsonPath.read(response.getBody(), "$.servers[0].url");
        assertTrue(serverUrl.startsWith("http://localhost:"), serverUrl);
    }

    @Test
    void openApiDocumentUsesHttpsWhenForwardedProtoIsHttps() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-Proto", "https");
        headers.set("X-Forwarded-Host", "shortener.example.test");

        ResponseEntity<String> response = restTemplate.exchange(
                "/v3/api-docs", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(OK, response.getStatusCode());
        String serverUrl = JsonPath.read(response.getBody(), "$.servers[0].url");
        assertTrue(serverUrl.startsWith("https://shortener.example.test"), serverUrl);
    }
}
