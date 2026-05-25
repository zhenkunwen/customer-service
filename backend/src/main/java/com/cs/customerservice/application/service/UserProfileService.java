package com.cs.customerservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserProfileService {

    private static final Logger log = LoggerFactory.getLogger(UserProfileService.class);
    private static final Duration TTL = Duration.ofDays(90);
    private static final int MAX_TOPICS = 10;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public UserProfileService(ReactiveRedisTemplate<String, Object> redisTemplate,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String profileKey(String tenantId, String userId) {
        return "cs:profile:" + tenantId + ":" + userId;
    }

    /**
     * 获取用户画像摘要文本，用于注入 system prompt
     */
    public Mono<String> getProfileSummary(String tenantId, String userId) {
        String key = profileKey(tenantId, userId);

        return Mono.zip(
                        redisTemplate.opsForHash().get(key, "tags").defaultIfEmpty(""),
                        redisTemplate.opsForHash().get(key, "summary").defaultIfEmpty(""),
                        redisTemplate.opsForHash().get(key, "sessionCount").defaultIfEmpty("0")
                )
                .map(tuple -> {
                    String tagsStr = tuple.getT1().toString();
                    String summary = tuple.getT2().toString();
                    int sessionCount = Integer.parseInt(tuple.getT3().toString());

                    if (tagsStr.isBlank() && summary.isBlank()) {
                        return "";
                    }

                    StringBuilder sb = new StringBuilder("【用户画像】");
                    if (!tagsStr.isBlank()) {
                        sb.append("标签：").append(tagsStr).append("。");
                    }
                    if (!summary.isBlank()) {
                        sb.append("历史总结：").append(summary).append("。");
                    }
                    sb.append("累计咨询次数：").append(sessionCount).append("。");
                    return sb.toString();
                })
                .defaultIfEmpty("");
    }

    /**
     * 记录一次交互，更新画像
     */
    public Mono<Void> recordInteraction(String tenantId, String userId, String question, String answer) {
        if (userId == null || userId.isBlank()) return Mono.empty();

        String key = profileKey(tenantId, userId);

        // 推断话题标签
        String topic = inferTopic(question);

        return redisTemplate.opsForHash().get(key, "recentTopics")
                .map(Object::toString)
                .defaultIfEmpty("[]")
                .flatMap(topicsJson -> {
                    List<String> topics;
                    try {
                        topics = objectMapper.readValue(topicsJson, new TypeReference<List<String>>() {});
                    } catch (Exception e) {
                        topics = new ArrayList<>();
                    }
                    topics.add(topic);
                    List<String> trimmed = topics.size() > MAX_TOPICS
                            ? topics.subList(topics.size() - MAX_TOPICS, topics.size())
                            : topics;
                    String newTopicsJson;
                    try {
                        newTopicsJson = objectMapper.writeValueAsString(trimmed);
                    } catch (JsonProcessingException e) {
                        newTopicsJson = "[]";
                    }
                    String finalTopicsJson = newTopicsJson;

                    return redisTemplate.opsForHash().get(key, "sessionCount")
                            .map(Object::toString)
                            .defaultIfEmpty("0")
                            .flatMap(countStr -> {
                                int count = Integer.parseInt(countStr) + 1;

                                Map<String, Object> updates = new HashMap<>();
                                updates.put("sessionCount", String.valueOf(count));
                                updates.put("recentTopics", finalTopicsJson);
                                updates.put("lastInteractionTime", Instant.now().toString());
                                updates.put("summary", buildSummary(trimmed));

                                return redisTemplate.opsForHash().putAll(key, updates)
                                        .flatMap(v -> redisTemplate.expire(key, TTL))
                                        .then();
                            });
                });
    }

    /**
     * 更新用户标签（如"夜间收货""不用塑料包装"等偏好）
     */
    public Mono<Void> addTag(String tenantId, String userId, String tag) {
        if (userId == null || userId.isBlank()) return Mono.empty();
        String key = profileKey(tenantId, userId);

        return redisTemplate.opsForHash().get(key, "tags")
                .map(Object::toString)
                .defaultIfEmpty("[]")
                .flatMap(tagsJson -> {
                    List<String> tags;
                    try {
                        tags = objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {});
                    } catch (Exception e) {
                        tags = new ArrayList<>();
                    }
                    if (!tags.contains(tag)) {
                        tags.add(tag);
                    }
                    try {
                        return redisTemplate.opsForHash().put(key, "tags",
                                        objectMapper.writeValueAsString(tags))
                                .flatMap(v -> redisTemplate.expire(key, TTL))
                                .then();
                    } catch (JsonProcessingException e) {
                        return Mono.empty();
                    }
                });
    }

    private String buildSummary(List<String> recentTopics) {
        if (recentTopics.isEmpty()) return "";
        Map<String, Long> freq = new HashMap<>();
        for (String t : recentTopics) {
            freq.merge(t, 1L, Long::sum);
        }
        String mostCommon = freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        if (mostCommon.isBlank()) return "";
        return "近期主要咨询" + mostCommon + "相关问题";
    }

    private String inferTopic(String question) {
        String q = question.toLowerCase();
        if (q.contains("物流") || q.contains("快递") || q.contains("运单") || q.contains("到哪")) return "物流";
        if (q.contains("退货") || q.contains("退款") || q.contains("换货") || q.contains("退")) return "退货";
        if (q.contains("订单") || q.contains("下单") || q.contains("购买")) return "订单";
        if (q.contains("优惠") || q.contains("券") || q.contains("折扣")) return "优惠";
        if (q.contains("密码") || q.contains("账号") || q.contains("登录")) return "账户";
        if (q.contains("库存") || q.contains("有货") || q.contains("缺货")) return "库存";
        return "其他";
    }
}
