package com.cs.customerservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对话响应")
public class ChatResponse {

    @Schema(description = "会话 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String sessionId;

    @Schema(description = "AI 回答内容", example = "您好，查到了！订单 ORD-20240001 当前状态是：已发货。")
    private String answer;

    @Schema(description = "使用的模型名称", example = "deepseek-chat")
    private String model;

    @Schema(description = "工具调用记录列表（仅工具模式）")
    private List<ToolCallRecord> toolCalls;

    @Schema(description = "响应耗时（毫秒）", example = "1234")
    private long latencyMs;

    @Schema(description = "是否降级响应（AI 异常时返回降级文案）", example = "false")
    private boolean fallback;

    @Schema(description = "分布式追踪 ID，用于链路追踪和日志关联", example = "a1b2c3d4e5f6g7h8")
    private String traceId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "工具调用记录")
    public static class ToolCallRecord {
        @Schema(description = "工具名称", example = "orderTool")
        private String toolName;

        @Schema(description = "调用参数（JSON 字符串）", example = "{\"userId\":\"user-001\",\"orderId\":\"ORD-20240001\"}")
        private String arguments;

        @Schema(description = "工具返回结果（JSON 字符串）", example = "{\"orderId\":\"ORD-20240001\",\"status\":\"已发货\",\"amount\":\"299.00\"}")
        private String result;
    }
}
