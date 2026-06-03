package com.cs.customerservice.infrastructure.security;

import com.cs.customerservice.infrastructure.entity.AgentEntity;
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

@Component
@Order(2)
public class AgentAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AgentAuthFilter.class);
    private static final String HEADER_NAME = "X-Agent-Token";

    private final AgentTokenService agentTokenService;

    public AgentAuthFilter(AgentTokenService agentTokenService) {
        this.agentTokenService = agentTokenService;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (!path.startsWith("/api/v1/agent/") && !path.startsWith("/api/v1/tickets")
                && !path.startsWith("/api/v1/admin")) {
            return chain.filter(exchange);
        }

        if (path.equals("/api/v1/agent/login")) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, "缺少 Agent Token，请在 X-Agent-Token 请求头中提供");
        }

        return agentTokenService.validate(token)
                .flatMap(agent -> {
                    if (agent == null) {
                        return unauthorized(exchange, "Agent Token 无效或已过期");
                    }
                    exchange.getAttributes().put("agent", agent);
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String msg) {
        log.warn("Agent auth failed: {}", msg);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(("{\"error\":\"" + msg + "\"}").getBytes())));
    }
}
