package com.cs.customerservice.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class MultiLevelCacheService {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheService.class);
    private static final Duration REDIS_TTL = Duration.ofHours(1);

    private final Cache<String, String> caffeineCache;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public MultiLevelCacheService(Cache<String, String> caffeineCache,
                                  ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.caffeineCache = caffeineCache;
        this.redisTemplate = redisTemplate;
    }

    public Mono<Optional<String>> get(String tenantId, String question) {
        String key = buildKey(tenantId, question);

        String l1Value = caffeineCache.getIfPresent(key);
        if (l1Value != null) {
            log.debug("L1 cache hit: tenant={}", tenantId);
            return Mono.just(Optional.of(l1Value));
        }

        return redisTemplate.opsForValue()
                .get(key)
                .doOnNext(v -> {
                    if (v != null) {
                        log.debug("L2 cache hit: tenant={}", tenantId);
                        caffeineCache.put(key, v.toString());
                    }
                })
                .map(v -> v != null ? Optional.of(v.toString()) : Optional.<String>empty())
                .defaultIfEmpty(Optional.empty());
    }

    public Mono<Void> put(String tenantId, String question, String answer) {
        String key = buildKey(tenantId, question);
        caffeineCache.put(key, answer);
        return redisTemplate.opsForValue()
                .set(key, answer, REDIS_TTL)
                .doOnSuccess(v -> log.debug("Cache updated: key={}", key))
                .then();
    }

    private String buildKey(String tenantId, String question) {
        return "cs:faq:" + tenantId + ":" + hash(question);
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
