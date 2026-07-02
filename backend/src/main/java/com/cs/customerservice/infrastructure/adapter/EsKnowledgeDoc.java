package com.cs.customerservice.infrastructure.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EsKnowledgeDoc {
    private String id;
    private String tenantId;
    private String title;
    private String content;
    private double score;
    private List<Float> embedding;

    private String source;
    private String section;
    private int chunkIndex;
    private int totalChunks;
    private Integer pageNum;
    private String docType;
}
