package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_event_id")
    private Long transferEventId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(name = "emotion_level", length = 8)
    @Builder.Default
    private String emotionLevel = "L0";

    @Column(length = 128)
    private String topic;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(nullable = false, length = 16)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @Column(name = "ai_attempted_solutions", columnDefinition = "TEXT")
    private String aiAttemptedSolutions;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
