package com.cs.customerservice.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@Order(1)
public class ApiKeyAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String HEADER_NAME = "X-API-Key";

    private final Map<String, String> tenantApiKeys;

    public ApiKeyAuthFilter(SecurityProperties props) {
        this.tenantApiKeys = Map.copyOf(props.getTenantKeys());
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Actuator 和健康检查放行
        if (path.startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        // 只拦截 /api/ 路径，Agent/Ticket 路径由 AgentAuthFilter 处理
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }
        if (path.startsWith("/api/v1/agent/") || path.startsWith("/api/v1/tickets")
                || path.startsWith("/api/v1/admin")) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Missing X-API-Key header from {}", exchange.getRequest().getRemoteAddress());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap("{\"error\":\"缺少 API Key，请在 X-API-Key 请求头中提供\"}".getBytes())));
        }

        String tenantId = tenantApiKeys.entrySet().stream()
                .filter(e -> e.getValue().equals(apiKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);

        if (tenantId == null) {
            log.warn("Invalid API Key from {}", exchange.getRequest().getRemoteAddress());
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse()
                            .bufferFactory()
                            .wrap("{\"error\":\"API Key 无效\"}".getBytes())));
        }

        exchange.getAttributes().put("tenantId", tenantId);
        return chain.filter(exchange);
    }
}
