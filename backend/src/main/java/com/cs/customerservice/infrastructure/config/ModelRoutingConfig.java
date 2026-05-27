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
        private String strongModelName;          // 新增，null 时退化为单模型
        private double temperature = 0.7;
        private double strongTemperature = 0.5;  // 新增
        private int maxTokens = 2048;
        private int strongMaxTokens = 4096;      // 新增
        private boolean functionCallEnabled = true;
        private double difficultyThreshold = 0.6; // 新增
        private String baseUrl;
        private String apiKey;
    }

    public double resolveDifficultyThreshold(String tenantId) {
        TenantModelConfig config = tenants != null ? tenants.get(tenantId) : null;
        return config != null ? config.getDifficultyThreshold() : DEFAULT_DIFFICULTY_THRESHOLD;
    }

    public String resolveStrongModelName(String tenantId) {
        TenantModelConfig config = tenants != null ? tenants.get(tenantId) : null;
        if (config != null) {
            String strong = config.getStrongModelName();
            if (strong != null && !strong.isBlank()) {
                return strong;
            }
        }
        return null;
    }

    private static final double DEFAULT_DIFFICULTY_THRESHOLD = 0.6;

    @Data
    public static class GrayRelease {
        private boolean enabled = false;
        private String targetModel = "deepseek-chat";
        private int percentage = 0;
    }
}
