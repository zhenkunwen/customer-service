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
import java.util.*;

@Service
public class DeepSeekEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekEmbeddingService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    /** 简单 LRU 缓存，避免对相同文本重复调用 API */
    private final LinkedHashMap<String, List<Float>> cache;

    public DeepSeekEmbeddingService(
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${cs.knowledge.embedding-model:text-embedding-v2}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl + "/v1/embeddings")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.cache = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                return size() > 1000;
            }
        };
    }

    public Mono<List<Float>> embed(String text) {
        // 缓存命中
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
                    List<Float> vector = new ArrayList<>();
                    JsonNode embedding = node.get("data").get(0).get("embedding");
                    embedding.forEach(v -> vector.add(v.floatValue()));
                    cache.put(text, vector);
                    return vector;
                })
                .onErrorResume(e -> {
                    log.warn("Embedding API call failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 清理缓存（知识条目更新时调用） */
    public void evict(String text) {
        cache.remove(text);
    }
}
