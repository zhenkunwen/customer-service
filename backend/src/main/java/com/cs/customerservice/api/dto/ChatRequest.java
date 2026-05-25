package com.cs.customerservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "对话请求，支持普通、流式、工具三种模式")
public class ChatRequest {

    @NotBlank(message = "sessionId must not be blank")
    @Schema(description = "会话 ID，用于关联多轮对话", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sessionId;

    @NotBlank(message = "tenantId must not be blank")
    @Schema(description = "租户 ID，用于多租户路由和鉴权", example = "default", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantId;

    @NotBlank(message = "userId must not be blank")
    @Schema(description = "用户 ID", example = "user-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    @NotBlank(message = "question must not be blank")
    @Schema(description = "用户输入的问题", example = "帮我查一下订单 ORD-20240001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String question;

    @Schema(description = "是否启用流式模式（SSE 推送）", example = "false", defaultValue = "false")
    private boolean streamMode;

    @Schema(description = "是否启用工具调用模式（函数调用）", example = "false", defaultValue = "false")
    private boolean toolMode;
}
