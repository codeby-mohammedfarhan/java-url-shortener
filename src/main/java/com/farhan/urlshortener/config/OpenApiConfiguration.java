package com.farhan.urlshortener.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "URL Shortener API",
        version = "1.0.0",
        description = "Create short links for HTTP and HTTPS URLs, redirect visitors to their destinations, and review click and lifecycle statistics. The service validates submitted URLs and applies per-client rate limiting to link creation."))
public class OpenApiConfiguration {
}
