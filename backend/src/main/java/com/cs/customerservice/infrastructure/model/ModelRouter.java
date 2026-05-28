package com.cs.customerservice.infrastructure.model;

import com.cs.customerservice.application.ai.Difficulty;
import com.cs.customerservice.infrastructure.config.ModelRoutingConfig;
import com.cs.customerservice.infrastructure.config.ModelRoutingConfig.TenantModelConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private static final String DEFAULT_MODEL = "deepseek-chat";

    private final ModelRoutingConfig routingConfig;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final ChatModel defaultChatModel;
    private final String defaultBaseUrl;
    private final String defaultApiKey;
    private final RestClient.Builder restClientBuilder;

    public ModelRouter(ModelRoutingConfig routingConfig,
                       ChatModel defaultChatModel,
                       RestClient.Builder restClientBuilder,
                       @Value("${spring.ai.openai.base-url}") String defaultBaseUrl,
                       @Value("${spring.ai.openai.api-key}") String defaultApiKey) {
        this.routingConfig = routingConfig;
        this.defaultChatModel = defaultChatModel;
        this.restClientBuilder = restClientBuilder;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultApiKey = defaultApiKey;
    }

    public ChatClient resolve(String tenantId, String userId) {
        TenantModelConfig tenantCfg = routingConfig.getTenants() != null
                ? routingConfig.getTenants().get(tenantId)
                : null;

        String modelName = tenantCfg != null ? tenantCfg.getModelName() : DEFAULT_MODEL;

        if (routingConfig.getGray() != null && routingConfig.getGray().isEnabled()) {
            modelName = resolveGrayModel(userId, modelName);
        }

        final String finalModelName = modelName;
        String cacheKey = tenantId + ":" + finalModelName;
        return clientCache.computeIfAbsent(cacheKey, k -> buildClient(tenantCfg, finalModelName));
    }

    private String resolveGrayModel(String userId, String currentModel) {
        ModelRoutingConfig.GrayRelease gray = routingConfig.getGray();
        int hash = Math.abs(userId.hashCode());
        if (hash % 100 < gray.getPercentage()) {
            log.debug("Gray routing: userId={} routed to model={}", userId, gray.getTargetModel());
            return gray.getTargetModel();
        }
        return currentModel;
    }

    private ChatClient buildClient(TenantModelConfig tenantCfg, String modelName) {
        TenantModelConfig effectiveCfg = tenantCfg != null ? tenantCfg
                : new TenantModelConfig();
        double temp = effectiveCfg.getTemperature();
        int tokens = effectiveCfg.getMaxTokens();
        return buildClient(tenantCfg, modelName, temp, tokens);
    }

    private ChatClient buildClient(TenantModelConfig tenantCfg, String modelName,
                                   double temperature, int maxTokens) {
        // If it's the default model on the shared API, reuse the auto-configured ChatModel
        if ((tenantCfg == null || tenantCfg.getBaseUrl() == null)
                && DEFAULT_MODEL.equals(modelName)) {
            return ChatClient.builder(defaultChatModel).build();
        }

        // Otherwise: need a dedicated OpenAiChatModel with the target model
        log.info("Building dedicated ChatClient: model={} temp={} maxTokens={} customBaseUrl={}",
                modelName, temperature, maxTokens, tenantCfg != null && tenantCfg.getBaseUrl() != null);

        String baseUrl = (tenantCfg != null && tenantCfg.getBaseUrl() != null)
                ? tenantCfg.getBaseUrl() : defaultBaseUrl;
        String apiKey = (tenantCfg != null && tenantCfg.getApiKey() != null)
                ? tenantCfg.getApiKey() : defaultApiKey;

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    public boolean isFunctionCallEnabled(String tenantId) {
        TenantModelConfig cfg = routingConfig.getTenants() != null
                ? routingConfig.getTenants().get(tenantId)
                : null;
        return cfg == null || cfg.isFunctionCallEnabled();
    }

    public String resolveModelName(String tenantId, String userId) {
        TenantModelConfig cfg = routingConfig.getTenants() != null
                ? routingConfig.getTenants().get(tenantId)
                : null;
        String model = cfg != null ? cfg.getModelName() : DEFAULT_MODEL;
        if (routingConfig.getGray() != null && routingConfig.getGray().isEnabled()) {
            model = resolveGrayModel(userId, model);
        }
        return model;
    }

    public ChatClient resolveByDifficulty(String tenantId, String userId, Difficulty difficulty) {
        TenantModelConfig tenantCfg = routingConfig.getTenants() != null
                ? routingConfig.getTenants().get(tenantId)
                : null;

        String modelName;
        double effectiveTemp;
        int effectiveTokens;
        if (tenantCfg != null && difficulty == Difficulty.COMPLEX
                && tenantCfg.getStrongModelName() != null
                && !tenantCfg.getStrongModelName().isBlank()) {
            modelName = tenantCfg.getStrongModelName();
            effectiveTemp = tenantCfg.getStrongTemperature();
            effectiveTokens = tenantCfg.getStrongMaxTokens();
        } else {
            modelName = tenantCfg != null ? tenantCfg.getModelName() : DEFAULT_MODEL;
            effectiveTemp = tenantCfg != null ? tenantCfg.getTemperature() : 0.7;
            effectiveTokens = tenantCfg != null ? tenantCfg.getMaxTokens() : 2048;
        }

        if (routingConfig.getGray() != null && routingConfig.getGray().isEnabled()) {
            modelName = resolveGrayModel(userId, modelName);
        }

        final String finalModelName = modelName;
        final double temperature = effectiveTemp;
        final int maxTokens = effectiveTokens;
        log.info("resolveByDifficulty: tenant={} userId={} difficulty={} -> model={} temp={} tokens={}",
                tenantId, userId, difficulty, finalModelName, temperature, maxTokens);
        String cacheKey = tenantId + ":" + finalModelName;
        return clientCache.computeIfAbsent(cacheKey,
                k -> buildClient(tenantCfg, finalModelName, temperature, maxTokens));
    }

    public String resolveModelNameByDifficulty(String tenantId, String userId, Difficulty difficulty) {
        TenantModelConfig cfg = routingConfig.getTenants() != null
                ? routingConfig.getTenants().get(tenantId)
                : null;
        if (cfg != null && difficulty == Difficulty.COMPLEX
                && cfg.getStrongModelName() != null
                && !cfg.getStrongModelName().isBlank()) {
            String model = cfg.getStrongModelName();
            if (routingConfig.getGray() != null && routingConfig.getGray().isEnabled()) {
                model = resolveGrayModel(userId, model);
            }
            return model;
        }
        return resolveModelName(tenantId, userId);
    }
}
