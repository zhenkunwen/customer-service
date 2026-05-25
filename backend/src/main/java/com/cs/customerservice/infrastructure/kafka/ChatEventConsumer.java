package com.cs.customerservice.infrastructure.kafka;

import com.cs.customerservice.infrastructure.entity.ChatRecord;
import com.cs.customerservice.infrastructure.entity.ChatRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class ChatEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventConsumer.class);

    private final ChatRecordRepository repository;
    private final ObjectMapper objectMapper;

    public ChatEventConsumer(ChatRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "customer-chat-events", groupId = "cs-consumer-group")
    public void consume(String message, Acknowledgment ack) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);

            ChatRecord record = ChatRecord.builder()
                    .sessionId((String) event.get("sessionId"))
                    .tenantId((String) event.get("tenantId"))
                    .userId((String) event.get("userId"))
                    .model((String) event.getOrDefault("model", "unknown"))
                    .question((String) event.get("question"))
                    .answer((String) event.get("answer"))
                    .toolCalls(objectMapper.writeValueAsString(event.getOrDefault("toolCalls", "")))
                    .latencyMs(event.get("latencyMs") instanceof Number
                            ? ((Number) event.get("latencyMs")).longValue() : 0L)
                    .status("ARCHIVED")
                    .createdAt(Instant.now())
                    .build();

            repository.save(record);
            ack.acknowledge();
            log.debug("Chat record archived: session={}", record.getSessionId());
        } catch (Exception e) {
            log.error("Failed to persist chat event", e);
            throw new RuntimeException("Consumer failed", e);
        }
    }
}
