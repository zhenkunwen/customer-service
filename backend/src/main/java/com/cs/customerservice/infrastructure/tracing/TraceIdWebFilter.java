package com.cs.customerservice.infrastructure.tracing;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(0)
public class TraceIdWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdWebFilter.class);
    public static final String TRACE_ID_ATTR = "traceId";

    private final Tracer tracer;

    public TraceIdWebFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return chain.filter(exchange)
                .contextWrite(ctx -> {
                    CurrentTraceContext currentTraceContext = tracer.currentTraceContext();
                    TraceContext traceContext = currentTraceContext.context();
                    String traceId = traceContext != null ? traceContext.traceId() : "unknown";
                    exchange.getAttributes().put(TRACE_ID_ATTR, traceId);
                    try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId)) {
                        log.debug("TraceId: {}", traceId);
                    }
                    return ctx;
                });
    }
}
