package com.cs.customerservice.application.eval;

import com.cs.customerservice.api.dto.ChatRequest;
import com.cs.customerservice.api.dto.ChatResponse;
import com.cs.customerservice.application.orchestrator.CustomerChatOrchestrator;
import com.cs.customerservice.application.service.KnowledgeRetrievalPort;
import com.cs.customerservice.domain.KnowledgeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);
    private static final int TOP_K = 3;
    private static final String EVAL_USER_ID = "eval-system";
    private static final String JUDGE_SYSTEM_PROMPT =
            "你是一个严格的评分员。根据提供的知识库上下文，评测客服回答的质量。输出必须是严格的 JSON 格式，不要包含其他文字。";

    private final KnowledgeRetrievalPort knowledgeRetrievalPort;
    private final CustomerChatOrchestrator orchestrator;
    private final ChatClient judgeClient;
    private final ObjectMapper objectMapper;

    public EvalService(KnowledgeRetrievalPort knowledgeRetrievalPort,
                       CustomerChatOrchestrator orchestrator,
                       ChatClient.Builder chatClientBuilder,
                       ObjectMapper objectMapper) {
        this.knowledgeRetrievalPort = knowledgeRetrievalPort;
        this.orchestrator = orchestrator;
        this.judgeClient = chatClientBuilder
                .defaultSystem(JUDGE_SYSTEM_PROMPT)
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .temperature(0.0)
                        .build())
                .build();
        this.objectMapper = objectMapper;
    }

    /** 加载测试用例 */
    public List<EvalTestCase> loadTestCases() {
        Yaml yaml = new Yaml();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("eval/eval-testcases.yaml")) {
            if (is == null) {
                log.warn("eval-testcases.yaml not found, returning empty list");
                return List.of();
            }
            Map<String, Object> root = yaml.load(is);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawList = (List<Map<String, Object>>) root.get("testcases");
            if (rawList == null) return List.of();

            List<EvalTestCase> cases = new ArrayList<>();
            for (Map<String, Object> raw : rawList) {
                EvalTestCase tc = new EvalTestCase();
                tc.setId((String) raw.get("id"));
                tc.setQuery((String) raw.get("query"));
                @SuppressWarnings("unchecked")
                List<String> expectedIds = (List<String>) raw.get("expectedChunkIds");
                tc.setExpectedChunkIds(expectedIds != null ? expectedIds : List.of());
                tc.setExpectedAnswer((String) raw.getOrDefault("expectedAnswer", ""));
                tc.setTenantId((String) raw.getOrDefault("tenantId", "default"));
                cases.add(tc);
            }
            return cases;
        } catch (Exception e) {
            log.warn("Failed to load test cases: {}", e.getMessage());
            return List.of();
        }
    }

    /** 执行完整评测 */
    public Mono<EvalReport> runEvaluation(String tenantId) {
        List<EvalTestCase> allCases = loadTestCases();
        List<EvalTestCase> filtered = tenantId != null && !tenantId.isBlank()
                ? allCases.stream().filter(c -> tenantId.equals(c.getTenantId())).collect(Collectors.toList())
                : allCases;

        if (filtered.isEmpty()) {
            return Mono.just(new EvalReport(tenantId, Instant.now(),
                    new EvalReport.Summary(), List.of()));
        }

        return Flux.fromIterable(filtered)
                .flatMap(tc -> evaluateSingle(tc).subscribeOn(Schedulers.boundedElastic()))
                .collectList()
                .map(details -> {
                    EvalReport.Summary summary = aggregate(details);
                    return new EvalReport(tenantId, Instant.now(), summary, details);
                });
    }

    /** 评测单条用例 */
    private Mono<EvalReport.Detail> evaluateSingle(EvalTestCase tc) {
        long retrievalStart = System.currentTimeMillis();
        return knowledgeRetrievalPort.search(tc.getTenantId(), tc.getQuery(), TOP_K)
                .flatMap(chunks -> {
                    long retrievalLatency = System.currentTimeMillis() - retrievalStart;

                    List<String> retrievedIds = chunks.stream()
                            .map(KnowledgeChunk::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    List<String> expectedIds = tc.getExpectedChunkIds() != null
                            ? tc.getExpectedChunkIds() : List.of();

                    double recall = computeRecall(retrievedIds, expectedIds);
                    double precision = computePrecision(retrievedIds, expectedIds);
                    boolean hit = computeHit(retrievedIds, expectedIds);
                    double mrr = computeMrr(retrievedIds, expectedIds);

                    if (tc.getExpectedAnswer() != null && !tc.getExpectedAnswer().isEmpty()) {
                        return doLayer2(tc, chunks, retrievedIds, expectedIds,
                                recall, precision, hit, mrr, retrievalLatency);
                    }

                    return Mono.just(new EvalReport.Detail(
                            tc.getId(), tc.getQuery(), retrievedIds, expectedIds,
                            recall, precision, hit, mrr,
                            null, null, null, null, null, retrievalLatency, 0));
                })
                .onErrorResume(e -> {
                    log.warn("Eval failed for case {}: {}", tc.getId(), e.getMessage());
                    return Mono.just(new EvalReport.Detail(
                            tc.getId(), tc.getQuery(), List.of(),
                            tc.getExpectedChunkIds() != null ? tc.getExpectedChunkIds() : List.of(),
                            0, 0, false, 0,
                            null, null, null, null, e.getMessage(), 0, 0));
                });
    }

    /** Layer 2: 端到端对话 + LLM Judge 评分 */
    private Mono<EvalReport.Detail> doLayer2(EvalTestCase tc, List<KnowledgeChunk> chunks,
                                              List<String> retrievedIds, List<String> expectedIds,
                                              double recall, double precision, boolean hit, double mrr,
                                              long retrievalLatency) {
        long chatStart = System.currentTimeMillis();
        String sessionId = "eval-" + UUID.randomUUID();

        ChatRequest request = ChatRequest.builder()
                .sessionId(sessionId)
                .tenantId(tc.getTenantId())
                .userId(EVAL_USER_ID)
                .question(tc.getQuery())
                .streamMode(false)
                .toolMode(false)
                .build();

        return orchestrator.chat(request, false, false)
                .flatMap(response -> {
                    long chatLatency = System.currentTimeMillis() - chatStart;
                    String answer = response.getAnswer();

                    return callJudge(tc.getQuery(), chunks, answer)
                            .map(judgeResult -> new EvalReport.Detail(
                                    tc.getId(), tc.getQuery(), retrievedIds, expectedIds,
                                    recall, precision, hit, mrr,
                                    answer,
                                    judgeResult.getCorrectness(),
                                    judgeResult.getFaithfulness(),
                                    judgeResult.getRelevance(),
                                    judgeResult.getReason(),
                                    retrievalLatency, chatLatency))
                            .onErrorResume(e -> {
                                log.warn("Judge call failed for case {}: {}", tc.getId(), e.getMessage());
                                return Mono.just(new EvalReport.Detail(
                                        tc.getId(), tc.getQuery(), retrievedIds, expectedIds,
                                        recall, precision, hit, mrr,
                                        answer, null, null, null, "Judge failed: " + e.getMessage(),
                                        retrievalLatency, chatLatency));
                            });
                })
                .onErrorResume(e -> {
                    log.warn("Chat failed for case {}: {}", tc.getId(), e.getMessage());
                    return Mono.just(new EvalReport.Detail(
                            tc.getId(), tc.getQuery(), retrievedIds, expectedIds,
                            recall, precision, hit, mrr,
                            null, null, null, null, "Chat failed: " + e.getMessage(),
                            retrievalLatency, System.currentTimeMillis() - chatStart));
                });
    }

    /** 调用 LLM Judge */
    private Mono<JudgeResult> callJudge(String query, List<KnowledgeChunk> chunks, String answer) {
        String context = chunks.stream()
                .map(KnowledgeChunk::getContent)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n---\n"));

        String prompt = String.format("""
                判断以下客服回答的质量。

                用户问题：%s

                知识库上下文：
                %s

                客服回答：%s

                请按以下维度评分（1-5分），并给出理由：
                1. correctness：回答是否正确（与知识库对比）
                2. faithfulness：是否严格基于知识库，不编造
                3. relevance：回答是否直接回应用户问题

                输出严格 JSON 格式（不要包含其他文字）：
                {"correctness": <1-5>, "faithfulness": <1-5>, "relevance": <1-5>, "reason": "..."}
                """, query, context, answer);

        return Mono.fromCallable(() -> {
            String result = judgeClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return parseJudgeResult(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private JudgeResult parseJudgeResult(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JudgeResult r = new JudgeResult();
            r.correctness = node.has("correctness") ? node.get("correctness").asDouble() : null;
            r.faithfulness = node.has("faithfulness") ? node.get("faithfulness").asDouble() : null;
            r.relevance = node.has("relevance") ? node.get("relevance").asDouble() : null;
            r.reason = node.has("reason") ? node.get("reason").asText() : "";
            return r;
        } catch (Exception e) {
            log.warn("Failed to parse judge result: {}", e.getMessage());
            JudgeResult r = new JudgeResult();
            r.reason = "Parse failed: " + e.getMessage();
            return r;
        }
    }

    // ========== 指标计算 ==========

    public static double computeRecall(List<String> retrieved, List<String> expected) {
        if (expected.isEmpty()) return 0;
        long hitCount = retrieved.stream().filter(expected::contains).count();
        return (double) hitCount / expected.size();
    }

    public static double computePrecision(List<String> retrieved, List<String> expected) {
        if (retrieved.isEmpty()) return 0;
        long hitCount = retrieved.stream().filter(expected::contains).count();
        return (double) hitCount / retrieved.size();
    }

    public static boolean computeHit(List<String> retrieved, List<String> expected) {
        return retrieved.stream().anyMatch(expected::contains);
    }

    public static double computeMrr(List<String> retrieved, List<String> expected) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (expected.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0;
    }

    /** 聚合汇总 */
    private EvalReport.Summary aggregate(List<EvalReport.Detail> details) {
        if (details.isEmpty()) return new EvalReport.Summary();

        int n = details.size();
        double totalRecall = 0, totalPrecision = 0, totalHit = 0, totalMrr = 0;
        double totalCorrectness = 0, totalFaithfulness = 0, totalRelevance = 0;
        int judgeCount = 0;

        for (EvalReport.Detail d : details) {
            totalRecall += d.getRecall();
            totalPrecision += d.getPrecision();
            if (d.isHit()) totalHit++;
            totalMrr += d.getMrr();

            if (d.getCorrectness() != null) {
                totalCorrectness += d.getCorrectness();
                totalFaithfulness += d.getFaithfulness() != null ? d.getFaithfulness() : 0;
                totalRelevance += d.getRelevance() != null ? d.getRelevance() : 0;
                judgeCount++;
            }
        }

        return new EvalReport.Summary(
                n,
                totalRecall / n,
                totalPrecision / n,
                totalHit / n,
                totalMrr / n,
                judgeCount > 0 ? totalCorrectness / judgeCount : 0,
                judgeCount > 0 ? totalFaithfulness / judgeCount : 0,
                judgeCount > 0 ? totalRelevance / judgeCount : 0
        );
    }

    /** LLM Judge 内部结果类 */
    private static class JudgeResult {
        Double correctness;
        Double faithfulness;
        Double relevance;
        String reason;

        Double getCorrectness() { return correctness; }
        Double getFaithfulness() { return faithfulness; }
        Double getRelevance() { return relevance; }
        String getReason() { return reason; }
    }
}
