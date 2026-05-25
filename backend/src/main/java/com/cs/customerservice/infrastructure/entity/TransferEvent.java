package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "transfer_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String question;

    @Column(length = 8)
    private String emotionLevel;

    @Column(length = 128)
    private String topic;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String attemptedSolutions;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(nullable = false)
    @Builder.Default
    private Boolean resolved = false;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
