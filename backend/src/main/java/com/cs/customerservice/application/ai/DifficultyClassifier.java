package com.cs.customerservice.application.ai;

import com.cs.customerservice.infrastructure.config.ModelRoutingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Component
public class DifficultyClassifier {

    private static final Logger log = LoggerFactory.getLogger(DifficultyClassifier.class);

    // 简单问题关键词 — 命中任意一个 + 低情绪 = SIMPLE
    private static final List<String> SIMPLE_KEYWORDS = List.of(
            "查订单", "查物流", "快递", "运单", "到哪", "订单号",
            "什么时候到", "几天到", "多少钱", "价格", "优惠",
            "你好", "在吗", "谢谢", "好的"
    );

    // 困难问题关键词 — 命中任意一个 = COMPLEX
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "退货", "退款", "换货", "投诉", "赔偿", "纠纷",
            "举报", "差评", "气死", "垃圾"
    );

    private static final int SHORT_QUESTION_THRESHOLD = 15;

    private final ModelRoutingConfig routingConfig;
    private final ChatClient classificationClient;

    public DifficultyClassifier(ModelRoutingConfig routingConfig,
                                ChatClient.Builder chatClientBuilder) {
        this.routingConfig = routingConfig;
        this.classificationClient = chatClientBuilder.build();
    }

    public Mono<Difficulty> classify(String tenantId, String question, String emotionLevel, String topic) {
        if (question == null || question.isBlank()) {
            return Mono.just(Difficulty.SIMPLE);
        }

        // 阶段 1: 规则判断
        Difficulty ruleResult = classifyByRule(question, emotionLevel);
        if (ruleResult != null) {
            log.debug("Rule classification: question='{}' -> {}", truncate(question), ruleResult);
            return Mono.just(ruleResult);
        }

        // 阶段 2: LLM 分类兜底
        return classifyByLlm(tenantId, question);
    }

    private Difficulty classifyByRule(String question, String emotionLevel) {
        String q = question.toLowerCase();

        // 情绪 L3 → 直接困难
        if ("L3".equals(emotionLevel)) {
            return Difficulty.COMPLEX;
        }

        // 困难关键词
        for (String kw : COMPLEX_KEYWORDS) {
            if (q.contains(kw)) {
                return Difficulty.COMPLEX;
            }
        }

        // 简单关键词 + 低情绪
        boolean hasSimpleKw = false;
        for (String kw : SIMPLE_KEYWORDS) {
            if (q.contains(kw)) {
                hasSimpleKw = true;
                break;
            }
        }
        if (hasSimpleKw && ("L0".equals(emotionLevel) || "L1".equals(emotionLevel))) {
            return Difficulty.SIMPLE;
        }

        // 短问题 → 简单
        if (q.length() < SHORT_QUESTION_THRESHOLD && !q.contains("?")) {
            return Difficulty.SIMPLE;
        }

        return null; // 不确定
    }

    private Mono<Difficulty> classifyByLlm(String tenantId, String question) {
        double threshold = routingConfig.resolveDifficultyThreshold(tenantId);

        return Mono.fromCallable(() -> {
            String response = classificationClient.prompt()
                    .system("""
                            你是一个问题复杂度分类器。判断用户问题是否需要深度推理。
                            仅返回一个 0-1 之间的小数，不要任何其他文字。
                            0.0 = 非常简单（问候、查单号）
                            1.0 = 非常复杂（纠纷、投诉、多步推理）
                            """)
                    .user(question)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return Difficulty.SIMPLE;
            }

            try {
                double score = Double.parseDouble(response.trim());
                log.debug("LLM classification: question='{}' score={} threshold={}",
                        truncate(question), score, threshold);
                return score >= threshold ? Difficulty.COMPLEX : Difficulty.SIMPLE;
            } catch (NumberFormatException e) {
                log.warn("LLM classification parse failed: '{}', default to SIMPLE", response);
                return Difficulty.SIMPLE;
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private static String truncate(String s) {
        return s != null && s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
