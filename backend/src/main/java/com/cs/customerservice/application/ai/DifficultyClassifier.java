package com.cs.customerservice.application.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class DifficultyClassifier {

    private static final Logger log = LoggerFactory.getLogger(DifficultyClassifier.class);

    private static final List<String> SIMPLE_KEYWORDS = List.of(
            "查订单", "查物流", "快递", "运单", "到哪", "订单号",
            "什么时候到", "几天到", "多少钱", "价格", "优惠",
            "你好", "在吗", "谢谢", "好的"
    );

    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "退货", "退款", "换货", "投诉", "赔偿", "纠纷",
            "举报", "差评", "气死", "垃圾"
    );

    private static final int SHORT_QUESTION_THRESHOLD = 15;

    public Mono<Difficulty> classify(String tenantId, String question, String emotionLevel, String topic) {
        if (question == null || question.isBlank()) {
            return Mono.just(Difficulty.SIMPLE);
        }

        Difficulty result = classifyByRule(question, emotionLevel);
        log.debug("Classification: question='{}' -> {}", truncate(question), result);
        return Mono.just(result);
    }

    private Difficulty classifyByRule(String question, String emotionLevel) {
        String q = question.toLowerCase();

        if ("L3".equals(emotionLevel)) {
            return Difficulty.COMPLEX;
        }

        for (String kw : COMPLEX_KEYWORDS) {
            if (q.contains(kw)) {
                return Difficulty.COMPLEX;
            }
        }

        boolean hasSimpleKw = SIMPLE_KEYWORDS.stream().anyMatch(q::contains);
        if (hasSimpleKw && ("L0".equals(emotionLevel) || "L1".equals(emotionLevel))) {
            return Difficulty.SIMPLE;
        }

        if (q.length() < SHORT_QUESTION_THRESHOLD && !q.contains("?")) {
            return Difficulty.SIMPLE;
        }

        return Difficulty.SIMPLE;
    }

    private static String truncate(String s) {
        return s != null && s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }
}
