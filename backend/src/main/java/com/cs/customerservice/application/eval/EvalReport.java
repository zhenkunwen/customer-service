package com.cs.customerservice.application.eval;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalReport {

    private String tenantId;
    private Instant evaluatedAt;
    private Summary summary;
    private List<Detail> details;

    /** 汇总指标 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int totalCases;
        private double avgRecall;
        private double avgPrecision;
        private double hitRate;
        private double avgMrr;
        private double avgCorrectness;
        private double avgFaithfulness;
        private double avgRelevance;
    }

    /** 单条用例详情 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Detail {
        private String caseId;
        private String query;
        private List<String> retrievedChunkIds;
        private List<String> expectedChunkIds;
        private double recall;
        private double precision;
        private boolean hit;
        private double mrr;
        private String answer;
        private Double correctness;
        private Double faithfulness;
        private Double relevance;
        private String judgeReason;
        private long retrievalLatencyMs;
        private long chatLatencyMs;
    }
}
