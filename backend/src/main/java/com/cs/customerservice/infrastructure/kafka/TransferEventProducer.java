package com.cs.customerservice.infrastructure.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class TransferEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransferEventProducer.class);
    private static final String TOPIC = "transfer-events";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TransferEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public Mono<Void> send(String sessionId, String payload) {
        return Mono.<Void>fromRunnable(() -> {
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, sessionId, payload);
            record.headers().add("sessionId", sessionId.getBytes(StandardCharsets.UTF_8));
            record.headers().add("timestamp", String.valueOf(System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8));

            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to send transfer event: session={}", sessionId, ex);
                } else {
                    log.info("Transfer event sent: session={}, offset={}", sessionId,
                            result.getRecordMetadata().offset());
                }
            });
        })
                .subscribeOn(Schedulers.boundedElastic())
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("Transfer event send skipped (Kafka unavailable): {}", e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
