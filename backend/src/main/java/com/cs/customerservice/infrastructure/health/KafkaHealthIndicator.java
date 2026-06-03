package com.cs.customerservice.infrastructure.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Profile("!h2")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var result = adminClient.describeCluster(
                    new DescribeClusterOptions().timeoutMs(5000));
            String clusterId = result.clusterId().get(5, TimeUnit.SECONDS);
            int nodeCount = result.nodes().get(5, TimeUnit.SECONDS).size();
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodes", nodeCount)
                    .withDetail("bootstrapServers", kafkaAdmin.getConfigurationProperties().get("bootstrap.servers"))
                    .build();
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("bootstrapServers", kafkaAdmin.getConfigurationProperties().get("bootstrap.servers"))
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
