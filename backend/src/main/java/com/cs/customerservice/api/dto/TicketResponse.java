package com.cs.customerservice.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private Long transferEventId;
    private String tenantId;
    private String sessionId;
    private String question;
    private String emotionLevel;
    private String topic;
    private Integer priority;
    private String status;
    private Long assignedAgentId;
    private String aiAttemptedSolutions;
    private String resolution;
    private Instant createdAt;
    private Instant updatedAt;
}
