package com.cs.customerservice.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic chatEventsTopic() {
        return TopicBuilder.name("customer-chat-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatEventsDlxTopic() {
        return TopicBuilder.name("customer-chat-events-dlx")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transferEventsTopic() {
        return TopicBuilder.name("transfer-events")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
