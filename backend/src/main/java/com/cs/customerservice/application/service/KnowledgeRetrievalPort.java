package com.cs.customerservice.application.service;

import com.cs.customerservice.domain.KnowledgeChunk;
import reactor.core.publisher.Mono;

import java.util.List;

public interface KnowledgeRetrievalPort {
    Mono<List<KnowledgeChunk>> search(String tenantId, String query, int topK);
}
