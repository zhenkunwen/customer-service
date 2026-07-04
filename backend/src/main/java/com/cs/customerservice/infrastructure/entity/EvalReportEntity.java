package com.cs.customerservice.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "eval_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "total_cases", nullable = false)
    private Integer totalCases;

    @Column(name = "avg_recall")
    private Double avgRecall;

    @Column(name = "avg_precision")
    private Double avgPrecision;

    @Column(name = "hit_rate")
    private Double hitRate;

    @Column(name = "avg_mrr")
    private Double avgMrr;

    @Column(name = "avg_correctness")
    private Double avgCorrectness;

    @Column(name = "avg_faithfulness")
    private Double avgFaithfulness;

    @Column(name = "avg_relevance")
    private Double avgRelevance;

    @Column(name = "details_json", columnDefinition = "MEDIUMTEXT")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
