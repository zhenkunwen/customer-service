package com.cs.customerservice.api.controller;

import com.cs.customerservice.infrastructure.config.DegradationConfig;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class DegradationController {

    private final DegradationConfig degradationConfig;

    public DegradationController(DegradationConfig degradationConfig) {
        this.degradationConfig = degradationConfig;
    }

    @GetMapping("/degradation")
    public Mono<Map<String, Object>> status(ServerWebExchange exchange) {
        AgentEntity agent = exchange.getAttribute("agent");
        if (agent == null || !"ADMIN".equals(agent.getRole())) {
            throw new IllegalStateException("仅管理员可查看降级状态");
        }
        return Mono.just(Map.of(
                "degradationEnabled", degradationConfig.isEnabled(),
                "mode", degradationConfig.isEnabled() ? "DEGRADED" : "NORMAL"
        ));
    }

    @PostMapping("/degradation/toggle")
    public Mono<Map<String, Object>> toggle(@RequestParam boolean enabled, ServerWebExchange exchange) {
        AgentEntity agent = exchange.getAttribute("agent");
        if (agent == null || !"ADMIN".equals(agent.getRole())) {
            throw new IllegalStateException("仅管理员可切换降级模式");
        }
        degradationConfig.setEnabled(enabled);
        return Mono.just(Map.of(
                "degradationEnabled", enabled,
                "mode", enabled ? "DEGRADED" : "NORMAL",
                "message", enabled ? "系统已进入降级模式，所有 AI 请求将返回兜底响应" : "系统已恢复正常模式"
        ));
    }

    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(Exception e) {
        return Map.of("error", e.getMessage());
    }
}
