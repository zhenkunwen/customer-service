package com.cs.customerservice.api.controller;

import com.cs.customerservice.api.dto.ChatRecordResponse;
import com.cs.customerservice.api.dto.ChatRequest;
import com.cs.customerservice.api.dto.ChatResponse;
import com.cs.customerservice.application.orchestrator.CustomerChatOrchestrator;
import com.cs.customerservice.infrastructure.entity.ChatRecord;
import com.cs.customerservice.infrastructure.entity.ChatRecordRepository;
import com.cs.customerservice.infrastructure.model.ModelRouter;
import com.cs.customerservice.infrastructure.tracing.TraceIdWebFilter;
import com.cs.customerservice.support.security.PromptGuardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cs")
@Tag(name = "智能客服", description = "智能客服对话 API，支持普通对话、流式对话和工具调用三种模式")
public class CustomerChatController {

    private static final Logger log = LoggerFactory.getLogger(CustomerChatController.class);

    private final CustomerChatOrchestrator orchestrator;
    private final ModelRouter modelRouter;
    private final PromptGuardService promptGuardService;
    private final ChatRecordRepository chatRecordRepository;

    public CustomerChatController(CustomerChatOrchestrator orchestrator,
                                  ModelRouter modelRouter,
                                  PromptGuardService promptGuardService,
                                  ChatRecordRepository chatRecordRepository) {
        this.orchestrator = orchestrator;
        this.modelRouter = modelRouter;
        this.promptGuardService = promptGuardService;
        this.chatRecordRepository = chatRecordRepository;
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "获取会话消息", description = "获取指定会话的所有消息（AI回复 + 客服消息），按时间升序排列。客户前端轮询此接口获取新消息。")
    public Mono<List<ChatRecordResponse>> sessionMessages(@PathVariable String sessionId) {
        return Mono.fromCallable(() ->
                chatRecordRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                        .stream().map(this::toChatRecordDto).collect(Collectors.toList())
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/chat")
    @Operation(summary = "普通对话", description = "发送消息并获取完整响应，支持工具调用模式。返回完整的 ChatResponse，包含回答、模型信息、耗时和可选工具调用结果。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "对话成功",
            content = @Content(schema = @Schema(implementation = ChatResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败"),
        @ApiResponse(responseCode = "401", description = "缺少 API Key"),
        @ApiResponse(responseCode = "403", description = "API Key 无效"),
        @ApiResponse(responseCode = "429", description = "请求频率超限"),
        @ApiResponse(responseCode = "500", description = "服务内部错误")
    })
    public Mono<ChatResponse> chat(@Valid @RequestBody ChatRequest request, ServerWebExchange exchange) {
        String traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTR);
        log.info("POST /chat: session={}, tenant={}, toolMode={}",
                request.getSessionId(), request.getTenantId(), request.isToolMode());
        return Mono.just(request)
                .flatMap(req -> orchestrator.chat(req, false, req.isToolMode()))
                .timeout(Duration.ofSeconds(120))
                .doOnNext(resp -> saveChatRecord(request, resp))
                .onErrorResume(PromptGuardService.PromptGuardException.class,
                        e -> Mono.just(ChatResponse.builder()
                                .sessionId(request.getSessionId())
                                .answer(e.getMessage())
                                .model("guard")
                                .latencyMs(0)
                                .fallback(true)
                                .build()))
                .onErrorResume(e -> {
                    log.error("Chat error: session={}", request.getSessionId(), e);
                    return orchestrator.chatFallback(request, false, false, e);
                })
                .doOnNext(resp -> resp.setTraceId(traceId));
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "流式对话", description = "通过 SSE (Server-Sent Events) 流式传输对话响应。事件类型: token（文本片段）、done（结束信号）、error（错误信息）。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "流式连接建立成功，持续推送 token 事件",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE)),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败"),
        @ApiResponse(responseCode = "401", description = "缺少 API Key"),
        @ApiResponse(responseCode = "403", description = "API Key 无效")
    })
    public Flux<ServerSentEvent<String>> chatStream(@Valid @RequestBody ChatRequest request, ServerWebExchange exchange) {
        String traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTR);
        log.info("POST /chat/stream: session={}, tenant={}",
                request.getSessionId(), request.getTenantId());

        return Flux.concat(
                Flux.just(ServerSentEvent.<String>builder()
                        .event("trace")
                        .data(traceId != null ? traceId : "unknown")
                        .build()),
                orchestrator.chatStream(request)
                        .map(token -> ServerSentEvent.<String>builder()
                                .event("token")
                                .data(token)
                                .build())
                        .concatWithValues(ServerSentEvent.<String>builder()
                                .event("done")
                                .data("[DONE]")
                                .build())
        )
                .timeout(Duration.ofSeconds(60))
                .doOnCancel(() -> log.info("Stream cancelled: session={}", request.getSessionId()))
                .doOnTerminate(() -> log.info("Stream terminated: session={}", request.getSessionId()))
                .onErrorResume(PromptGuardService.PromptGuardException.class,
                        e -> Flux.just(ServerSentEvent.<String>builder()
                                .event("error")
                                .data(e.getMessage())
                                .build()))
                .onErrorResume(e -> {
                    log.error("Stream error: session={}", request.getSessionId(), e);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("流式响应中断: " + e.getMessage())
                            .build());
                });
    }

    private void saveChatRecord(ChatRequest request, ChatResponse response) {
        try {
            ChatRecord record = ChatRecord.builder()
                    .sessionId(request.getSessionId())
                    .tenantId(request.getTenantId())
                    .userId(request.getUserId() != null ? request.getUserId() : "")
                    .model(response.getModel() != null ? response.getModel() : "unknown")
                    .question(request.getQuestion())
                    .answer(response.getAnswer())
                    .latencyMs(response.getLatencyMs())
                    .status("ARCHIVED")
                    .createdAt(Instant.now())
                    .build();
            chatRecordRepository.save(record);
        } catch (Exception e) {
            log.warn("Failed to save chat record (non-critical): {}", e.getMessage());
        }
    }

    private ChatRecordResponse toChatRecordDto(ChatRecord r) {
        return ChatRecordResponse.builder()
                .id(r.getId()).userId(r.getUserId()).model(r.getModel())
                .question(r.getQuestion()).answer(r.getAnswer())
                .latencyMs(r.getLatencyMs()).status(r.getStatus())
                .createdAt(r.getCreatedAt()).build();
    }

    @PostMapping("/chat/tool")
    @Operation(summary = "工具对话", description = "启用函数调用的对话模式。AI 可根据用户问题自动调用订单查询、物流追踪、退货政策等工具。功能同普通对话 toolMode=true。")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "工具调用对话成功",
            content = @Content(schema = @Schema(implementation = ChatResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求参数校验失败"),
        @ApiResponse(responseCode = "401", description = "缺少 API Key"),
        @ApiResponse(responseCode = "403", description = "API Key 无效"),
        @ApiResponse(responseCode = "429", description = "请求频率超限"),
        @ApiResponse(responseCode = "500", description = "服务内部错误")
    })
    public Mono<ChatResponse> chatTool(@Valid @RequestBody ChatRequest request, ServerWebExchange exchange) {
        String traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTR);
        log.info("POST /chat/tool: session={}, tenant={}",
                request.getSessionId(), request.getTenantId());
        return Mono.just(request)
                .flatMap(req -> orchestrator.chat(req, false, true))
                .timeout(Duration.ofSeconds(120))
                .doOnNext(resp -> saveChatRecord(request, resp))
                .onErrorResume(PromptGuardService.PromptGuardException.class,
                        e -> Mono.just(ChatResponse.builder()
                                .sessionId(request.getSessionId())
                                .answer(e.getMessage())
                                .model("guard")
                                .latencyMs(0)
                                .fallback(true)
                                .build()))
                .onErrorResume(e -> {
                    log.error("Tool chat error: session={}", request.getSessionId(), e);
                    return orchestrator.chatFallback(request, false, true, e);
                })
                .doOnNext(resp -> resp.setTraceId(traceId));
    }
}
