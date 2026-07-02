package com.cs.customerservice.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekEmbeddingService.class);

    private final WebClient webClient;
    private final String model;

    /** 线程安全的 LRU 缓存，避免对相同文本重复调用 API */
    private final Map<String, List<Float>> cache;

    public DeepSeekEmbeddingService(
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${cs.knowledge.embedding-model:text-embedding-v2}") String model) {
        this.model = model;
        // 标准化 baseUrl，去除尾随斜杠
        String normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(normalizedUrl + "/v1/embeddings")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                        return size() > 1000;
                    }
                });
    }

    public Mono<List<Float>> embed(String text) {
        List<Float> cached = cache.get(text);
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.post()
                .bodyValue(Map.of("model", model, "input", text))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(node -> {
                    List<Float> vector = extractVector(node);
                    cache.put(text, vector);
                    return vector;
                })
                .doOnError(e -> log.warn("Embedding API call failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<Float> extractVector(JsonNode node) {
        JsonNode data = node.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            log.warn("Embedding API returned unexpected response: missing data array");
            return List.of();
        }
        List<Float> vector = new ArrayList<>();
        data.get(0).get("embedding").forEach(v -> vector.add(v.floatValue()));
        return vector;
    }

    /** 清理缓存（知识条目更新时调用） */
    public void evict(String text) {
        cache.remove(text);
    }
}
