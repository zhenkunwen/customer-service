package com.cs.customerservice.support.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CustomMetrics {

    private final MeterRegistry registry;

    public CustomMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRequest(String tenant, String model, String status) {
        Counter.builder("chat_requests_total")
                .tag("tenant", tenant)
                .tag("model", model)
                .tag("status", status)
                .description("Total chat requests")
                .register(registry)
                .increment();
    }

    public void recordToolCall(String tool, boolean success) {
        Counter.builder("chat_tool_calls_total")
                .tag("tool", tool)
                .tag("success", String.valueOf(success))
                .description("Total tool calls")
                .register(registry)
                .increment();
    }

    public void recordCacheHit(String level) {
        Counter.builder("chat_cache_hit_ratio")
                .tag("level", level)
                .description("Cache hit count per level")
                .register(registry)
                .increment();
    }

    public void recordFirstTokenLatency(long millis) {
        Timer.builder("chat_first_token_seconds")
                .description("Time to first token")
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
    }

    public void recordLlmLatency(long millis) {
        Timer.builder("chat_llm_latency_seconds")
                .description("LLM call total latency")
                .register(registry)
                .record(millis, TimeUnit.MILLISECONDS);
    }
}
