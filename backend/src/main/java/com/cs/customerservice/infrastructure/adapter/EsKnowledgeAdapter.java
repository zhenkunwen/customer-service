package com.cs.customerservice.infrastructure.adapter;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.cs.customerservice.application.ai.DeepSeekEmbeddingService;
import com.cs.customerservice.application.service.KnowledgeRetrievalPort;
import com.cs.customerservice.domain.KnowledgeChunk;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(value = "cs.knowledge.es.enabled", havingValue = "true", matchIfMissing = true)
public class EsKnowledgeAdapter implements KnowledgeRetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(EsKnowledgeAdapter.class);
    private static final String INDEX_NAME = "knowledge";

    private final ElasticsearchClient esClient;
    private final DeepSeekEmbeddingService embeddingService;

    public EsKnowledgeAdapter(ElasticsearchClient esClient, DeepSeekEmbeddingService embeddingService) {
        this.esClient = esClient;
        this.embeddingService = embeddingService;
    }

    @PostConstruct
    public void initIndex() {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (!exists) {
                esClient.indices().create(c -> c
                        .index(INDEX_NAME)
                        .settings(s -> s
                                .numberOfShards("1")
                                .numberOfReplicas("0"))
                        .mappings(m -> m
                                .properties("tenantId", p -> p.keyword(k -> k))
                                .properties("title", p -> p.text(t -> t))
                                .properties("content", p -> p.text(t -> t))
                                .properties("score", p -> p.double_(d -> d))
                                .properties("embedding", p -> p.denseVector(v -> v
                                        .dims(1024)
                                        .index(true)
                                        .similarity("cosine")))
                        )
                );
                log.info("Created ES index: {}", INDEX_NAME);
            } else {
                log.info("ES index already exists: {}", INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("Failed to init ES index (ES may be down): {}", e.getMessage());
        }
    }

    @Override
    public Mono<List<KnowledgeChunk>> search(String tenantId, String query, int topK) {
        return embeddingService.embed(query)
                .flatMap(vector -> doSearch(tenantId, query, vector, topK))
                .switchIfEmpty(Mono.just(List.of()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<List<KnowledgeChunk>> doSearch(String tenantId, String query, List<Float> vector, int topK) {
        return Mono.fromCallable(() -> {
            try {
                // 双路检索：knn 语义 + BM25 全文
                int poolSize = topK * 2; // 各取 2 倍候选池再融合

                // 1. knn 向量检索
                SearchResponse<EsKnowledgeDoc> knnResp = esClient.search(s -> s
                                .index(INDEX_NAME)
                                .knn(k -> k
                                        .field("embedding")
                                        .queryVector(vector)
                                        .k(poolSize)
                                        .numCandidates(200))
                                .query(q -> q
                                        .bool(b -> b
                                                .filter(f -> f
                                                        .term(t -> t
                                                                .field("tenantId")
                                                                .value(tenantId)))))
                                .size(poolSize),
                        EsKnowledgeDoc.class);

                // 2. BM25 全文检索
                SearchResponse<EsKnowledgeDoc> bm25Resp = esClient.search(s -> s
                                .index(INDEX_NAME)
                                .query(q -> q
                                        .bool(b -> b
                                                .must(m -> m
                                                        .match(t -> t
                                                                .field("content")
                                                                .query(query)))
                                                .filter(f -> f
                                                        .term(t -> t
                                                                .field("tenantId")
                                                                .value(tenantId)))))
                                .size(poolSize),
                        EsKnowledgeDoc.class);

                // 3. 手动 RRF 融合（按排名加权，不依赖 ES license）
                // score += 1.0 / (rank + rankConstant)
                Map<String, KnowledgeChunk> merged = new LinkedHashMap<>();
                double rankConstant = 60.0;

                // knn 排名贡献
                List<Hit<EsKnowledgeDoc>> knnHits = knnResp.hits().hits();
                for (int i = 0; i < knnHits.size(); i++) {
                    EsKnowledgeDoc doc = knnHits.get(i).source();
                    if (doc == null) continue;
                    KnowledgeChunk chunk = toChunk(doc);
                    chunk.setScore(1.0 / (i + rankConstant));
                    merged.put(doc.getId(), chunk);
                }

                // BM25 排名贡献（叠加）
                List<Hit<EsKnowledgeDoc>> bm25Hits = bm25Resp.hits().hits();
                for (int i = 0; i < bm25Hits.size(); i++) {
                    EsKnowledgeDoc doc = bm25Hits.get(i).source();
                    if (doc == null) continue;
                    KnowledgeChunk existing = merged.get(doc.getId());
                    if (existing != null) {
                        existing.setScore(existing.getScore() + 1.0 / (i + rankConstant));
                    } else {
                        KnowledgeChunk chunk = toChunk(doc);
                        chunk.setScore(1.0 / (i + rankConstant));
                        merged.put(doc.getId(), chunk);
                    }
                }

                // 按 RRF 总分排序取 topK
                return merged.values().stream()
                        .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                        .limit(topK)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("ES hybrid search failed (tenantId={}): {}", tenantId, e.getMessage());
                return List.of();
            }
        });
    }

    /** 将 ES 文档转为领域对象 */
    private static KnowledgeChunk toChunk(EsKnowledgeDoc doc) {
        return KnowledgeChunk.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .score(doc.getScore())
                .build();
    }

    public Mono<Void> save(KnowledgeChunk chunk) {
        return embeddingService.embed(chunk.getTitle() + " " + chunk.getContent())
                .flatMap(vector -> Mono.fromRunnable(() -> {
                    try {
                        String docId = chunk.getId() != null ? chunk.getId() : UUID.randomUUID().toString();
                        esClient.index(i -> i
                                .index(INDEX_NAME)
                                .id(docId)
                                .document(EsKnowledgeDoc.builder()
                                        .id(docId)
                                        .tenantId(chunk.getTenantId())
                                        .title(chunk.getTitle())
                                        .content(chunk.getContent())
                                        .score(chunk.getScore())
                                        .embedding(vector)
                                        .build()));
                        log.info("Saved knowledge doc: id={}, title={}", docId, chunk.getTitle());
                    } catch (Exception e) {
                        log.warn("Failed to save knowledge doc: {}", e.getMessage());
                    }
                }).subscribeOn(Schedulers.boundedElastic()).then());
    }

    public Mono<Void> delete(String docId) {
        return Mono.fromRunnable(() -> {
            try {
                esClient.delete(d -> d.index(INDEX_NAME).id(docId));
                log.info("Deleted knowledge doc: id={}", docId);
            } catch (Exception e) {
                log.warn("Failed to delete knowledge doc: {}", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<List<KnowledgeChunk>> listByTenant(String tenantId) {
        return Mono.fromCallable(() -> {
            try {
                SearchResponse<EsKnowledgeDoc> response = esClient.search(s -> s
                                .index(INDEX_NAME)
                                .query(q -> q
                                        .term(t -> t.field("tenantId").value(tenantId)))
                                .size(1000),
                        EsKnowledgeDoc.class);
                return response.hits().hits().stream()
                        .map(Hit::source)
                        .filter(Objects::nonNull)
                        .map(doc -> KnowledgeChunk.builder()
                                .id(doc.getId())
                                .tenantId(doc.getTenantId())
                                .title(doc.getTitle())
                                .content(doc.getContent())
                                .score(doc.getScore())
                                .build())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to list knowledge docs: {}", e.getMessage());
                return List.<KnowledgeChunk>of();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
