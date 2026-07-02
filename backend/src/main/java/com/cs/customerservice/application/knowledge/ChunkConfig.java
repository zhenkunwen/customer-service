package com.cs.customerservice.application.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cs.knowledge.chunk")
public class ChunkConfig {

    private Map<String, Strategy> strategies = Map.of(
            "tech",       new Strategy(300, 800, 80),
            "narrative",  new Strategy(500, 1200, 120),
            "policy",     new Strategy(400, 1500, 100)
    );

    private Strategy fallback = new Strategy(400, 1000, 100);

    public Strategy getStrategy(String docType) {
        return strategies.getOrDefault(docType, fallback);
    }

    public record Strategy(int minSize, int maxSize, int overlap) {}

    public Map<String, Strategy> getStrategies() { return strategies; }
    public void setStrategies(Map<String, Strategy> strategies) { this.strategies = strategies; }
    public Strategy getFallback() { return fallback; }
    public void setFallback(Strategy fallback) { this.fallback = fallback; }
}
