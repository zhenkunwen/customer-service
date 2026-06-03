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

/**
 * 对话记忆服务
 * <p>
 * 负责管理用户与客服系统之间的对话历史。核心功能包括：
 * <ul>
 *   <li>存储每条对话消息（用户提问 + AI 回答）到 Redis List 结构</li>
 *   <li>加载最近的 N 条消息（用于构建对话上下文）</li>
 *   <li>当消息数量超过阈值时，自动触发摘要生成，将长对话压缩为简短摘要，避免上下文过长导致 token 浪费</li>
 *   <li>摘要生成使用专门的小型模型（summaryChatClient）异步执行，并缓存到 Redis</li>
 *   <li>支持设置 TTL（生存时间），会话数据 12 小时自动过期，防止内存无限增长</li>
 * </ul>
 * 采用响应式编程（Reactive Redis Template），非阻塞操作。
 * </p>
 * 
 * @author Your Name
 * @version 1.0
 */
@Service  // 标记为Spring服务组件，由容器管理
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    // ========== 配置常量 ==========
    private static final int MAX_RECENT_MESSAGES = 8;   // 最多保留最近的消息条数（用于上下文）
    private static final int SUMMARIZE_THRESHOLD = 20;   // 消息总数超过该阈值时触发摘要生成
    private static final Duration TTL = Duration.ofHours(12);  // Redis 中对话数据的过期时间（12小时）

    // ========== 依赖组件 ==========
    private final ReactiveRedisTemplate<String, Object> redisTemplate;  // 响应式 Redis 客户端，用于存取消息和摘要
    private final ObjectMapper objectMapper;                            // Jackson JSON 序列化工具
    private final ChatClient summaryChatClient;                         // 专门用于生成摘要的 AI 客户端（通常使用轻量模型）

    /**
     * 构造方法，通过 Spring 依赖注入初始化组件
     * 
     * @param redisTemplate     响应式 Redis 模板，操作 List 和 String 结构
     * @param objectMapper      对象映射器，将消息 Map 转为 JSON 字符串存储
     * @param summaryChatClient 用于生成对话摘要的 AI 客户端（使用 @Qualifier 区分主聊天客户端）
     */
    public ConversationMemoryService(ReactiveRedisTemplate<String, Object> redisTemplate,
                                     ObjectMapper objectMapper,
                                     @Qualifier("summaryChatClient") ChatClient summaryChatClient) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.summaryChatClient = summaryChatClient;
    }

    // ========== Redis Key 构建方法 ==========

    /**
     * 构建存储消息列表的 Redis Key
     * <p>
     * 格式：cs:session:{sessionId}:messages
     * 使用 Redis List 结构，右侧追加新消息，左端为最早消息。
     * </p>
     * 
     * @param sessionId 会话唯一标识
     * @return Redis Key 字符串
     */
    private String messagesKey(String sessionId) {
        return "cs:session:" + sessionId + ":messages";
    }

    /**
     * 构建存储对话摘要的 Redis Key
     * <p>
     * 格式：cs:session:{sessionId}:summary
     * 使用 String 结构保存摘要文本。
     * </p>
     * 
     * @param sessionId 会话唯一标识
     * @return Redis Key 字符串
     */
    private String summaryKey(String sessionId) {
        return "cs:session:" + sessionId + ":summary";
    }

    // ========== 公共 API ==========

    /**
     * 加载最近的若干条消息（最多 MAX_RECENT_MESSAGES 条）
     * <p>
     * 从 Redis List 的右端（最新）向左取 limit 条消息。每条消息存储为 Map 结构，
     * 包含 role（user/assistant）和 content（文本内容）。返回时，将 Map 序列化为 JSON 字符串，
     * 方便调用方直接使用或解析。
     * </p>
     * 
     * @param sessionId 会话 ID
     * @param limit     期望加载的消息条数（实际不会超过 MAX_RECENT_MESSAGES）
     * @return 消息 JSON 字符串列表，按照时间从旧到新排列（若未取满则列表较短）
     */
    @SuppressWarnings("unchecked")  // 抑制 Redis 返回 Object 转 Map 时的类型安全警告
    public Mono<List<String>> loadRecentMessages(String sessionId, int limit) {
        // 从 Redis List 中截取尾部最近的 limit 条（索引负值表示从尾部计数）
        return redisTemplate.opsForList()
                .range(messagesKey(sessionId), -Math.min(limit, MAX_RECENT_MESSAGES), -1)
                // 将每个元素（可能是 Map 或 其他类型）转为 JSON 字符串
                .map(obj -> {
                    if (obj instanceof Map) {
                        try {
                            return objectMapper.writeValueAsString(obj);
                        } catch (JsonProcessingException e) {
                            // 序列化失败时降级为 toString
                            return obj.toString();
                        }
                    }
                    return obj.toString();
                })
                .collectList()                 // 收集为 List<String>
                .defaultIfEmpty(List.of());    // 若 Redis 中无数据，返回空列表
    }

    /**
     * 加载会话的摘要信息
     * <p>
     * 摘要由 triggerSummarize 方法生成并缓存，如果从未生成过或已过期，返回空字符串。
     * 调用方应妥善处理空摘要的情况。
     * </p>
     * 
     * @param sessionId 会话 ID
     * @return 摘要文本的 Mono，若没有摘要则返回空字符串
     */
    public Mono<String> loadSummary(String sessionId) {
        return redisTemplate.opsForValue()
                .get(summaryKey(sessionId))
                .map(Object::toString)
                .defaultIfEmpty("");   // 无摘要时返回空字符串而非 null
    }

    /**
     * 向会话中追加一条消息（无论是用户消息还是 AI 回复）
     * <p>
     * 消息以 Map 形式存储，包含 role 和 content。存入 Redis List 右侧（最新位置）。
     * 每次追加后会检查列表长度，如果超过 SUMMARIZE_THRESHOLD 则异步触发摘要生成，
     * 以压缩历史信息。同时会刷新 List 和后续摘要的 TTL（过期时间）。
     * </p>
     * 
     * @param sessionId 会话 ID
     * @param role      角色："user" 或 "assistant"
     * @param content   消息内容
     * @return 完成信号的 Mono
     */
    public Mono<Void> appendMessage(String sessionId, String role, String content) {
        // 构建消息 Map，使用 LinkedHashMap 保持字段顺序（role 在前，content 在后）
        Map<String, String> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);

        // 将消息压入 Redis List 的右侧（尾部）
        return redisTemplate.opsForList()
                .rightPush(messagesKey(sessionId), msg)
                .flatMap(size -> {
                    // 每次添加后刷新 List 的 TTL（过期时间），使其延长为 TTL 时长
                    redisTemplate.expire(messagesKey(sessionId), TTL).subscribe();
                    // 如果当前列表长度超过了摘要生成阈值，则触发异步摘要生成（不阻塞主流程）
                    if (size != null && size > SUMMARIZE_THRESHOLD) {
                        return triggerSummarize(sessionId);
                    }
                    return Mono.empty();  // 未达到阈值，什么也不做
                })
                .then();  // 忽略 flatMap 的返回值，最终返回 Mono<Void>
    }

    /**
     * 强制触发摘要生成（也可由 appendMessage 自动调用）
     * <p>
     * 加载最近 MAX_RECENT_MESSAGES 条消息，调用 summaryChatClient（轻量模型）生成 100 字内的中文摘要，
     * 然后将摘要存入 Redis，并设置相同的 TTL。摘要生成过程中若出现异常（超时、模型错误），
     * 会返回一个占位符 "(摘要暂时不可用)"，确保不阻断后续流程。
     * </p>
     * 
     * @param sessionId 会话 ID
     * @return 完成信号的 Mono
     */
    public Mono<Void> triggerSummarize(String sessionId) {
        // 加载最近 MAX_RECENT_MESSAGES 条消息
        return loadRecentMessages(sessionId, MAX_RECENT_MESSAGES)
                .flatMap(messages -> {
                    // 将消息列表拼接成一个文本块，每条消息单独一行（已经是 JSON 格式，但仍可读）
                    String conversationText = String.join("\n", messages);
                    // 构造摘要提示词：要求用简短中文摘要对话要点，100字以内，直接输出摘要内容
                    String prompt = "请用简短中文摘要以下对话要点（100字以内，直接输出摘要内容）：\n\n" + conversationText;

                    // 调用 AI 模型生成摘要（使用 fromCallable 包装同步调用为 Mono）
                    return Mono.fromCallable(() ->
                                    summaryChatClient.prompt()
                                            .user(prompt)
                                            .call()
                                            .content())
                            .timeout(Duration.ofSeconds(10))   // 摘要生成最多等待10秒，防止模型过慢阻塞
                            .onErrorResume(e -> {               // 出错时返回降级摘要
                                log.warn("Summary generation failed for session={}: {}", sessionId, e.getMessage());
                                return Mono.just("(摘要暂时不可用)");
                            })
                            .flatMap(summary ->
                                    // 将生成的摘要存入 Redis String，并设置 TTL
                                    redisTemplate.opsForValue()
                                            .set(summaryKey(sessionId), summary, TTL)
                                            .then()
                            );
                })
                .then();  // 返回 Mono<Void>，调用方不需要关心摘要的返回内容
    }
}