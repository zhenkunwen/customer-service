package com.cs.customerservice.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ChatEventDlxHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatEventDlxHandler.class);

    @KafkaListener(topics = "customer-chat-events-dlx", groupId = "cs-dlx-group")
    public void handleDlx(String message) {
        log.error("DLQ message received: {}", message);
        // 死信处理策略：记录到日志并触发告警，后续可接入人工排查或重放机制
    }
}
