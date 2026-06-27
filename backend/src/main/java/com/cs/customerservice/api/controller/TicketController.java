package com.cs.customerservice.api.controller;

import com.cs.customerservice.api.dto.ChatRecordResponse;
import com.cs.customerservice.api.dto.TicketResponse;
import com.cs.customerservice.api.dto.TicketStatsResponse;
import com.cs.customerservice.api.dto.TicketUpdateRequest;
import com.cs.customerservice.application.ticket.TicketService;
import com.cs.customerservice.infrastructure.entity.AgentEntity;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private static final Logger log = LoggerFactory.getLogger(TicketController.class);
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    private AgentEntity getAgent(ServerWebExchange exchange) {
        return exchange.getAttribute("agent");
    }

    @GetMapping
    public Mono<Page<TicketResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        PageRequest pageable = PageRequest.of(page, size);
        if ("ADMIN".equals(agent.getRole()) || "TEAM_LEAD".equals(agent.getRole())) {
            String s = status != null ? status : "PENDING";
            String t = tenantId != null ? tenantId : "default";
            return ticketService.listByStatus(s, t, pageable);
        }
        return ticketService.listByAgent(agent.getId(), pageable);
    }

    @GetMapping("/{id}")
    public Mono<TicketResponse> get(@PathVariable Long id) {
        return ticketService.findById(id);
    }

    @PostMapping
    public Mono<TicketResponse> create(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam String question,
            @RequestParam(defaultValue = "L0") String emotionLevel,
            @RequestParam(defaultValue = "其他") String topic,
            @RequestParam(defaultValue = "0") int priority,
            ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if (!"ADMIN".equals(agent.getRole()) && !"TEAM_LEAD".equals(agent.getRole())) {
            throw new IllegalStateException("无权限创建工单");
        }
        return ticketService.create(sessionId, tenantId, question, emotionLevel, topic, priority);
    }

    @PutMapping("/{id}/claim")
    public Mono<TicketResponse> claim(@PathVariable Long id, ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        return ticketService.claim(id, agent.getId());
    }

    @PutMapping("/{id}/assign")
    public Mono<TicketResponse> assign(@PathVariable Long id, @RequestParam Long agentId,
                                        ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if (!"ADMIN".equals(agent.getRole()) && !"TEAM_LEAD".equals(agent.getRole())) {
            throw new IllegalStateException("无权限执行派发操作");
        }
        return ticketService.assign(id, agentId);
    }

    @PutMapping("/{id}/resolve")
    public Mono<TicketResponse> resolve(@PathVariable Long id,
                                         @Valid @RequestBody TicketUpdateRequest request,
                                         ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        return ticketService.resolve(id, agent.getId(), request);
    }

    @PutMapping("/{id}/close")
    public Mono<TicketResponse> close(@PathVariable Long id, ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        return ticketService.close(id, agent.getId(), "ADMIN".equals(agent.getRole()));
    }

    @GetMapping("/{id}/chat-history")
    public Mono<List<ChatRecordResponse>> chatHistory(@PathVariable Long id) {
        return ticketService.getChatHistory(id);
    }

    @DeleteMapping("/{id}")
    public Mono<Map<String, String>> delete(@PathVariable Long id, ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if (!"ADMIN".equals(agent.getRole())) {
            throw new IllegalStateException("仅管理员可删除工单");
        }
        return ticketService.delete(id).thenReturn(Map.of("message", "工单已删除"));
    }

    @GetMapping("/stats")
    public Mono<TicketStatsResponse> stats(ServerWebExchange exchange) {
        AgentEntity agent = getAgent(exchange);
        if (!"ADMIN".equals(agent.getRole()) && !"TEAM_LEAD".equals(agent.getRole())) {
            throw new IllegalStateException("无权限查看统计数据");
        }
        return ticketService.stats();
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
