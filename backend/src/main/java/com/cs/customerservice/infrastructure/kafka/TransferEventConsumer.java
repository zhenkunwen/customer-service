package com.cs.customerservice.infrastructure.kafka;

import com.cs.customerservice.infrastructure.entity.TransferEvent;
import com.cs.customerservice.infrastructure.repository.TransferEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class TransferEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventConsumer.class);

    private final TransferEventRepository repository;
    private final ObjectMapper objectMapper;

    public TransferEventConsumer(TransferEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "transfer-events", groupId = "cs-transfer-group")
    public void consume(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            TransferEvent event = TransferEvent.builder()
                    .sessionId((String) data.getOrDefault("sessionId", ""))
                    .tenantId((String) data.getOrDefault("tenantId", ""))
                    .userId((String) data.getOrDefault("userId", ""))
                    .question((String) data.getOrDefault("question", ""))
                    .emotionLevel((String) data.getOrDefault("emotionLevel", "L0"))
                    .topic((String) data.getOrDefault("topic", "其他"))
                    .attemptedSolutions((String) data.getOrDefault("attemptedSolutions", ""))
                    .resolved(false)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            repository.save(event);
            log.info("Transfer event persisted: session={}, topic={}, emotion={}",
                    event.getSessionId(), event.getTopic(), event.getEmotionLevel());
        } catch (Exception e) {
            log.error("Failed to consume transfer event: {}", e.getMessage());
        }
    }
}
