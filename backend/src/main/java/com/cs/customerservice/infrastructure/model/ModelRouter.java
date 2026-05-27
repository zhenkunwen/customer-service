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
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelRoutingConfig routingConfig;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final ChatModel defaultChatModel;

    public ModelRouter(ModelRoutingConfig routingConfig,
                       ChatModel defaultChatModel) {
        this.routingConfig = routingConfig;
        this.defaultChatModel = defaultChatModel;
    }

    public ChatClient resolve(String tenantId, String userId) {
        TenantModelConfig tenantCfg = routingConfig.getTenants() != null
                ? routingConfig.getTenants().get(tenantId)
                : null;

        String modelName = tenantCfg != null ? tenantCfg.getModelName() : "deepseek-chat";

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
        double temp = tenantCfg != null ? tenantCfg.getTemperature() : 0.7;
        int tokens = tenantCfg != null ? tenantCfg.getMaxTokens() : 2048;
        return buildClient(tenantCfg, modelName, temp, tokens);
    }

    private ChatClient buildClient(TenantModelConfig tenantCfg, String modelName,
                                   double temperature, int maxTokens) {
        ChatModel chatModel;
        if (tenantCfg != null && tenantCfg.getBaseUrl() != null) {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(tenantCfg.getBaseUrl())
                    .apiKey(tenantCfg.getApiKey())
                    .build();
            chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(modelName)
                            .temperature(temperature)
                            .maxTokens(maxTokens)
                            .build())
                    .build();
        } else {
            chatModel = defaultChatModel;
        }

        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(modelName)
                        .temperature(temperature)
                        .maxTokens(maxTokens)
                        .build())
                .build();
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
        String model = cfg != null ? cfg.getModelName() : "deepseek-chat";
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
            modelName = tenantCfg != null ? tenantCfg.getModelName() : "deepseek-chat";
            effectiveTemp = tenantCfg != null ? tenantCfg.getTemperature() : 0.7;
            effectiveTokens = tenantCfg != null ? tenantCfg.getMaxTokens() : 2048;
        }

        if (routingConfig.getGray() != null && routingConfig.getGray().isEnabled()) {
            modelName = resolveGrayModel(userId, modelName);
        }

        final String finalModelName = modelName;
        final double temperature = effectiveTemp;
        final int maxTokens = effectiveTokens;
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
