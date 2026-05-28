package com.cs.customerservice.api.controller;

import com.cs.customerservice.api.dto.AgentLoginRequest;
import com.cs.customerservice.api.dto.AgentLoginResponse;
import com.cs.customerservice.application.agent.AgentService;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/login")
    public Mono<AgentLoginResponse> login(@Valid @RequestBody AgentLoginRequest request) {
        log.info("Agent login: username={}", request.getUsername());
        return agentService.login(request);
    }

    @PostMapping("/logout")
    public Mono<Map<String, String>> logout(@RequestHeader("X-Agent-Token") String token) {
        return agentService.logout(token)
                .thenReturn(Map.of("message", "已退出登录"));
    }

    @PostMapping("/register")
    public Mono<Map<String, Object>> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(defaultValue = "AGENT") String role,
            ServerWebExchange exchange) {
        AgentEntity currentAgent = exchange.getAttribute("agent");
        if (currentAgent == null || !"ADMIN".equals(currentAgent.getRole())) {
            throw new IllegalStateException("仅管理员可注册新客服账号");
        }
        return agentService.register(username, password, role)
                .map(agent -> Map.<String, Object>of(
                        "id", agent.getId(),
                        "username", agent.getUsername(),
                        "role", agent.getRole()
                ));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(Exception e) {
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleForbidden(Exception e) {
        return Map.of("error", e.getMessage());
    }
}
