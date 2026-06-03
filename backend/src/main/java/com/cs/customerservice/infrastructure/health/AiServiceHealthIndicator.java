package com.cs.customerservice.infrastructure.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class AiServiceHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AiServiceHealthIndicator.class);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public AiServiceHealthIndicator(@Value("${spring.ai.openai.base-url}") String baseUrl,
                                    @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Health health() {
        if (apiKey == null || apiKey.isBlank()) {
            return Health.unknown()
                    .withDetail("endpoint", baseUrl)
                    .withDetail("reason", "API key not configured, skipping check")
                    .build();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/models"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return Health.up()
                        .withDetail("endpoint", baseUrl)
                        .build();
            }
            log.warn("AI service returned status: {}", response.statusCode());
            return Health.down()
                    .withDetail("endpoint", baseUrl)
                    .withDetail("httpStatus", response.statusCode())
                    .build();
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("endpoint", baseUrl)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
