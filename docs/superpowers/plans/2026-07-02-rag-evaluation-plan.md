# RAG 评测系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建两层 RAG 评测：Layer 1 召回率（recall/precision/hit/MRR）+ Layer 2 回答质量（LLM Judge 打分）

**Architecture:** EvalService 加载 YAML 测试用例 → 对每条用例并行跑检索（Layer 1）和端到端对话（Layer 2）→ LLM Judge 给回答质量打分 → 聚合报告

**Key context from existing code:**
- `KnowledgeRetrievalPort.search(tenantId, query, topK)` → `Mono<List<KnowledgeChunk>>` — 直接做向量检索
- `CustomerChatOrchestrator.chat(ChatRequest, false, false)` → `Mono<ChatResponse>` — 完整对话链路
- `ChatRequest` 需要 `sessionId`, `tenantId`, `userId`, `question` 四个必填字段
- `ChatResponse` 有 `answer`, `model`, `latencyMs` 等字段
- SnakeYAML 已通过 `spring-boot-starter` 引入，无需新增依赖
- `ChatClient.Builder` 由 `spring-ai-openai-spring-boot-starter` 自动配置

---
### Task 1: EvalTestCase 模型 + 测试用例 YAML

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/eval/EvalTestCase.java`
- Create: `backend/src/main/resources/eval/eval-testcases.yaml`

- [ ] **Step 1: 创建 EvalTestCase 模型**

```java
package com.cs.customerservice.application.eval;

import java.util.List;

public class EvalTestCase {
    private String id;
    private String query;
    private List<String> expectedChunkIds;
    private String expectedAnswer;
    private String tenantId;

    public EvalTestCase() {}

    public EvalTestCase(String id, String query, List<String> expectedChunkIds, String expectedAnswer, String tenantId) {
        this.id = id;
        this.query = query;
        this.expectedChunkIds = expectedChunkIds;
        this.expectedAnswer = expectedAnswer;
        this.tenantId = tenantId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<String> getExpectedChunkIds() { return expectedChunkIds; }
    public void setExpectedChunkIds(List<String> expectedChunkIds) { this.expectedChunkIds = expectedChunkIds; }
    public String getExpectedAnswer() { return expectedAnswer; }
    public void setExpectedAnswer(String expectedAnswer) { this.expectedAnswer = expectedAnswer; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
```

- [ ] **Step 2: 创建测试用例 YAML 文件**

```yaml
testcases:
  - id: tc-001
    query: "商品怎么退货？"
    expectedChunkIds: []
    expectedAnswer: ""
    tenantId: "default"

  - id: tc-002
    query: "快递太慢了，都五天了"
    expectedChunkIds: []
    expectedAnswer: ""
    tenantId: "default"

  - id: tc-003
    query: "怎么使用优惠券"
    expectedChunkIds: []
    expectedAnswer: ""
    tenantId: "default"

  - id: tc-004
    query: "怎么联系人工客服"
    expectedChunkIds: []
    expectedAnswer: ""
    tenantId: "default"

  - id: tc-005
    query: "我的订单怎么还没到"
    expectedChunkIds: []
    expectedAnswer: ""
    tenantId: "default"
```

> `expectedChunkIds` 初始为空。用户后续需要根据实际上传的知识库文档的 chunk ID 来填充这些值。`expectedAnswer` 同理。

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add EvalTestCase model and eval-testcases.yaml"
```

---

### Task 2: EvalReport 模型

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/eval/EvalReport.java`

- [ ] **Step 1: 创建 EvalReport 模型**

```java
package com.cs.customerservice.application.eval;

import java.time.Instant;
import java.util.List;

public class EvalReport {

    private String tenantId;
    private Instant evaluatedAt;
    private Summary summary;
    private List<Detail> details;

    public EvalReport() {}

    public EvalReport(String tenantId, Instant evaluatedAt, Summary summary, List<Detail> details) {
        this.tenantId = tenantId;
        this.evaluatedAt = evaluatedAt;
        this.summary = summary;
        this.details = details;
    }

    public String getTenantId() { return tenantId; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
    public Summary getSummary() { return summary; }
    public List<Detail> getDetails() { return details; }

    /** 汇总指标 */
    public static class Summary {
        private int totalCases;
        private double avgRecall;
        private double avgPrecision;
        private double hitRate;
        private double avgMrr;
        private double avgCorrectness;
        private double avgFaithfulness;
        private double avgRelevance;

        public Summary() {}

        public Summary(int totalCases, double avgRecall, double avgPrecision, double hitRate, double avgMrr,
                       double avgCorrectness, double avgFaithfulness, double avgRelevance) {
            this.totalCases = totalCases;
            this.avgRecall = avgRecall;
            this.avgPrecision = avgPrecision;
            this.hitRate = hitRate;
            this.avgMrr = avgMrr;
            this.avgCorrectness = avgCorrectness;
            this.avgFaithfulness = avgFaithfulness;
            this.avgRelevance = avgRelevance;
        }

        public int getTotalCases() { return totalCases; }
        public double getAvgRecall() { return avgRecall; }
        public double getAvgPrecision() { return avgPrecision; }
        public double getHitRate() { return hitRate; }
        public double getAvgMrr() { return avgMrr; }
        public double getAvgCorrectness() { return avgCorrectness; }
        public double getAvgFaithfulness() { return avgFaithfulness; }
        public double getAvgRelevance() { return avgRelevance; }
    }

    /** 单条用例详情 */
    public static class Detail {
        private String caseId;
        private String query;
        private List<String> retrievedChunkIds;
        private List<String> expectedChunkIds;
        private double recall;
        private double precision;
        private boolean hit;
        private double mrr;
        private String answer;           // Layer 2 的 LLM 回答
        private Double correctness;      // null 表示未评测（无 expectedAnswer）
        private Double faithfulness;
        private Double relevance;
        private String judgeReason;      // LLM Judge 的评分理由
        private long retrievalLatencyMs;
        private long chatLatencyMs;

        public Detail() {}

        public Detail(String caseId, String query, List<String> retrievedChunkIds, List<String> expectedChunkIds,
                      double recall, double precision, boolean hit, double mrr,
                      String answer, Double correctness, Double faithfulness, Double relevance,
                      String judgeReason, long retrievalLatencyMs, long chatLatencyMs) {
            this.caseId = caseId;
            this.query = query;
            this.retrievedChunkIds = retrievedChunkIds;
            this.expectedChunkIds = expectedChunkIds;
            this.recall = recall;
            this.precision = precision;
            this.hit = hit;
            this.mrr = mrr;
            this.answer = answer;
            this.correctness = correctness;
            this.faithfulness = faithfulness;
            this.relevance = relevance;
            this.judgeReason = judgeReason;
            this.retrievalLatencyMs = retrievalLatencyMs;
            this.chatLatencyMs = chatLatencyMs;
        }

        public String getCaseId() { return caseId; }
        public String getQuery() { return query; }
        public List<String> getRetrievedChunkIds() { return retrievedChunkIds; }
        public List<String> getExpectedChunkIds() { return expectedChunkIds; }
        public double getRecall() { return recall; }
        public double getPrecision() { return precision; }
        public boolean isHit() { return hit; }
        public double getMrr() { return mrr; }
        public String getAnswer() { return answer; }
        public Double getCorrectness() { return correctness; }
        public Double getFaithfulness() { return faithfulness; }
        public Double getRelevance() { return relevance; }
        public String getJudgeReason() { return judgeReason; }
        public long getRetrievalLatencyMs() { return retrievalLatencyMs; }
        public long getChatLatencyMs() { return chatLatencyMs; }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: add EvalReport model with Summary and Detail"
```

---

### Task 3: EvalService（核心编排）

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/eval/EvalService.java`

**Dependencies:** EvalTestCase (Task 1), EvalReport (Task 2), `KnowledgeRetrievalPort`, `CustomerChatOrchestrator`, `ChatRequest`, `ChatClient.Builder`

- [ ] **Step 1: 创建 EvalService**

```java
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
    private static final String JUDGE_SYSTEM_PROMPT = """
            你是一个严格的评分员。根据提供的知识库上下文，评测客服回答的质量。
            输出必须是严格的 JSON 格式，不要包含其他文字。
            """;

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
            return Mono.just(new EvalReport(tenantId, Instant.now(), new EvalReport.Summary(), List.of()));
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
        // Layer 1: 检索召回评测
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

                    // 计算指标
                    double recall = computeRecall(retrievedIds, expectedIds);
                    double precision = computePrecision(retrievedIds, expectedIds);
                    boolean hit = computeHit(retrievedIds, expectedIds);
                    double mrr = computeMrr(retrievedIds, expectedIds);

                    // Layer 2: 回答质量评测（如果该用例有 expectedAnswer 才做）
                    if (tc.getExpectedAnswer() != null && !tc.getExpectedAnswer().isEmpty()) {
                        return doLayer2(tc, chunks, retrievedIds, expectedIds, recall, precision, hit, mrr, retrievalLatency);
                    }

                    // 没有 expectedAnswer，只返回 Layer 1 结果
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
        String sessionId = "eval-" + UUID.randomUUID().toString();

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

                    // LLM Judge 评分
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

    static double computeRecall(List<String> retrieved, List<String> expected) {
        if (expected.isEmpty()) return 0;
        long hitCount = retrieved.stream().filter(expected::contains).count();
        return (double) hitCount / expected.size();
    }

    static double computePrecision(List<String> retrieved, List<String> expected) {
        if (retrieved.isEmpty()) return 0;
        long hitCount = retrieved.stream().filter(expected::contains).count();
        return (double) hitCount / retrieved.size();
    }

    static boolean computeHit(List<String> retrieved, List<String> expected) {
        return retrieved.stream().anyMatch(expected::contains);
    }

    static double computeMrr(List<String> retrieved, List<String> expected) {
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
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add EvalService with recall and answer quality evaluation"
```

---

### Task 4: EvalController API

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/api/controller/EvalController.java`

- [ ] **Step 1: 创建 EvalController**

```java
package com.cs.customerservice.api.controller;

import com.cs.customerservice.application.eval.EvalReport;
import com.cs.customerservice.application.eval.EvalService;
import com.cs.customerservice.application.eval.EvalTestCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @PostMapping("/run")
    public Mono<EvalReport> runEvaluation(
            @RequestParam(defaultValue = "default") String tenantId) {
        return evalService.runEvaluation(tenantId);
    }

    @GetMapping("/testcases")
    public ResponseEntity<List<EvalTestCase>> listTestCases(
            @RequestParam(defaultValue = "default") String tenantId) {
        List<EvalTestCase> all = evalService.loadTestCases();
        List<EvalTestCase> filtered = all.stream()
                .filter(tc -> tenantId.equals(tc.getTenantId()))
                .toList();
        return ResponseEntity.ok(filtered);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add EvalController with /api/v1/eval/run and /testcases"
```

---

### 验证

- [ ] **启动应用并触发评测**

```bash
cd backend && mvn spring-boot:run -DskipTests
```

在另一个终端：
```bash
curl -s -X POST "http://localhost:8080/api/v1/eval/run?tenantId=default" | jq .
```

Expected: 返回 EvalReport JSON，包含 summary（avgRecall/avgPrecision/hitRate/avgMrr/avgCorrectness 等）和 details 数组。

- [ ] **查看测试用例列表**

```bash
curl -s "http://localhost:8080/api/v1/eval/testcases?tenantId=default" | jq .
```

Expected: 返回 YAML 中加载的测试用例列表。
