package com.cs.customerservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);
    private static final int MAX_RECENT_MESSAGES = 8;
    private static final int SUMMARIZE_THRESHOLD = 20;
    private static final Duration TTL = Duration.ofHours(12);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatClient summaryChatClient;

    public ConversationMemoryService(ReactiveRedisTemplate<String, Object> redisTemplate,
                                     ObjectMapper objectMapper,
                                     @Qualifier("summaryChatClient") ChatClient summaryChatClient) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.summaryChatClient = summaryChatClient;
    }

    private String messagesKey(String sessionId) {
        return "cs:session:" + sessionId + ":messages";
    }

    private String summaryKey(String sessionId) {
        return "cs:session:" + sessionId + ":summary";
    }

    @SuppressWarnings("unchecked")
    public Mono<List<String>> loadRecentMessages(String sessionId, int limit) {
        return redisTemplate.opsForList()
                .range(messagesKey(sessionId), -Math.min(limit, MAX_RECENT_MESSAGES), -1)
                .map(obj -> {
                    if (obj instanceof Map) {
                        try {
                            return objectMapper.writeValueAsString(obj);
                        } catch (JsonProcessingException e) {
                            return obj.toString();
                        }
                    }
                    return obj.toString();
                })
                .collectList()
                .defaultIfEmpty(List.of());
    }

    public Mono<String> loadSummary(String sessionId) {
        return redisTemplate.opsForValue()
                .get(summaryKey(sessionId))
                .map(Object::toString)
                .defaultIfEmpty("");
    }

    public Mono<Void> appendMessage(String sessionId, String role, String content) {
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);

        return redisTemplate.opsForList()
                .rightPush(messagesKey(sessionId), msg)
                .flatMap(size -> {
                    redisTemplate.expire(messagesKey(sessionId), TTL).subscribe();
                    if (size != null && size > SUMMARIZE_THRESHOLD) {
                        return triggerSummarize(sessionId);
                    }
                    return Mono.empty();
                })
                .then();
    }

    public Mono<Void> triggerSummarize(String sessionId) {
        return loadRecentMessages(sessionId, MAX_RECENT_MESSAGES)
                .flatMap(messages -> {
                    String conversationText = String.join("\n", messages);
                    String prompt = "请用简短中文摘要以下对话要点（100字以内，直接输出摘要内容）：\n\n" + conversationText;

                    return Mono.fromCallable(() ->
                            summaryChatClient.prompt()
                                    .user(prompt)
                                    .call()
                                    .content())
                            .timeout(Duration.ofSeconds(10))
                            .onErrorResume(e -> {
                                log.warn("Summary generation failed for session={}: {}", sessionId, e.getMessage());
                                return Mono.just("(摘要暂时不可用)");
                            })
                            .flatMap(summary ->
                                    redisTemplate.opsForValue()
                                            .set(summaryKey(sessionId), summary, TTL)
                                            .then());
                })
                .then();
    }
}
