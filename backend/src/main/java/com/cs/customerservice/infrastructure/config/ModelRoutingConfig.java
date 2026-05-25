package com.cs.customerservice.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "cs.routing")
public class ModelRoutingConfig {

    private Map<String, TenantModelConfig> tenants;
    private GrayRelease gray;

    @Data
    public static class TenantModelConfig {
        private String modelName = "deepseek-chat";
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private boolean functionCallEnabled = true;
        private String baseUrl;
        private String apiKey;
    }

    @Data
    public static class GrayRelease {
        private boolean enabled = false;
        private String targetModel = "deepseek-chat";
        private int percentage = 0;
    }
}
