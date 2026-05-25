package com.cs.customerservice.infrastructure.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class ChatEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventProducer.class);
    private static final String TOPIC = "customer-chat-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ChatEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> send(String sessionId, String payload) {
        return Mono.<Void>create(sink -> {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, sessionId, payload);
            record.headers().add("sessionId", sessionId.getBytes(StandardCharsets.UTF_8));
            record.headers().add("timestamp", String.valueOf(System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send kafka event: session={}", sessionId, ex);
                    sink.error(ex);
                } else {
                    log.debug("Kafka event sent: session={}, offset={}", sessionId,
                            result.getRecordMetadata().offset());
                    sink.success();
                }
            });
        }).then();
    }
}
