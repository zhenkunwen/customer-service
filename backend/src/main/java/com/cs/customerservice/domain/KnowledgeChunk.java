package com.cs.customerservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {
    private String id;
    private String tenantId;
    private String title;
    private String content;
    private double score;

    // 元数据：溯源与文档结构
    private String source;        // 来源文件名，如"退货政策_v2.pdf"
    private String section;       // 所属章节标题，如"第三章 退换货流程"
    private int chunkIndex;       // 本文档的第几块（0-based）
    private int totalChunks;      // 本文档总块数
    private Integer pageNum;      // 页码（PDF 特有）
    private String docType;       // "tech" | "narrative" | "policy"
}
