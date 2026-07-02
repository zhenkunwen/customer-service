# RAG 评测系统设计

## 概述

构建一套 RAG 评测体系，覆盖**检索召回率**和**端到端回答质量**两个维度，用自动化评分量化知识库效果，指导调优。

## 两层评测架构

```
Layer 1: 召回率评测                     Layer 2: 回答质量评测
┌──────────────────────┐               ┌──────────────────────────┐
│  query → ES knn 检索  │               │  query → 完整 Chat 链路  │
│  对比 retrieved vs     │               │  LLM Judge 打分          │
│  expected chunks      │               │  (正确性/忠实度/相关性)   │
│  输出 recall/precision │               │  输出 1-5 分              │
└──────────────────────┘               └──────────────────────────┘
         ↓                                        ↓
         └──────────── 综合报告 ──────────────────┘
```

### Layer 1：检索召回评测

- 对每条测试用例，调用 `EsKnowledgeAdapter.search()` 执行向量检索
- 将 topK 结果与 `expectedChunkIds` 对比
- 核心逻辑纯计算，不依赖 LLM

### Layer 2：回答质量评测

- 走完整 Chat 链路（含知识库上下文注入）
- 将 query + 检索结果 + LLM 回答送入 LLM Judge 打分
- LLM Judge 使用独立调用（temperature=0），避免被评分链路污染

## 测试用例

### 格式（YAML）

```yaml
# eval-testcases.yaml
- id: tc-001
  query: "商品怎么退货？"
  expectedChunkIds: ["faq-1"]
  expectedAnswer: "7天内可无理由退货..."
  tenantId: "default"

- id: tc-002
  query: "快递太慢了，都五天了"
  expectedChunkIds: ["faq-2"]
  expectedAnswer: ""
  tenantId: "default"
```

- `expectedChunkIds` — Layer 1 期望召回的 chunk ID 列表
- `expectedAnswer` — Layer 2 期望回答（空字符串表示只看召回，不评回答质量）
- 文件放在 `resources/eval/eval-testcases.yaml`，git 管理

## 评测指标

| 层 | 指标 | 计算公式 | 说明 |
|----|------|----------|------|
| 召回率 | Recall@k | \|retrieved ∩ expected\| / \|expected\| | 期望有多少被召回 |
| 召回率 | Precision@k | \|retrieved ∩ expected\| / k | 结果中有多少是期望的 |
| 召回率 | Hit@k | has\_any\_hit ? 1 : 0 | 至少有一条期望被召回 |
| 召回率 | MRR@k | 1 / rank\_of\_first\_hit | 首个命中的排位倒数 |
| 回答质量 | correctness | LLM Judge 1-5 | 回答是否正确 |
| 回答质量 | faithfulness | LLM Judge 1-5 | 是否基于知识库不编造 |
| 回答质量 | relevance | LLM Judge 1-5 | 回答是否相关 |

## LLM Judge

使用独立的 DeepSeek Chat 调用（temperature=0），prompt：

```
你是一个评分员。判断以下客服回答是否正确、是否基于知识库、是否相关。

问题：{query}
知识库上下文：{retrievedChunks}
回答：{answer}

请输出 JSON：
{"correctness": 1-5, "faithfulness": 1-5, "relevance": 1-5, "reason": "..."}
```

- correctness：回答内容是否正确（与 expectedAnswer 对比 + 常识判断）
- faithfulness：回答是否严格基于提供的知识库上下文，不编造事实
- relevance：回答是否直接回应了用户问题

## 新增组件

| 文件 | 类型 | 职责 |
|------|------|------|
| `application/eval/EvalTestCase.java` | Create | 测试用例模型 + YAML 加载 |
| `application/eval/EvalService.java` | Create | 编排：加载用例→跑召回→跑对话→Judge 评分→聚合报告 |
| `application/eval/EvalReport.java` | Create | 评测报告模型（含汇总 + 明细） |
| `api/controller/EvalController.java` | Create | API 入口：触发评测、查看报告 |
| `resources/eval/eval-testcases.yaml` | Create | 测试用例文件 |

### 关键接口

```java
// EvalService.java
public Mono<EvalReport> runEvaluation(String tenantId) {
    // 1. 加载测试用例
    // 2. 遍历每条用例：
    //    a. Layer 1: 检索 → 算 recall/precision/hit/MRR
    //    b. Layer 2: 走 Chat 链路 → LLM Judge 打分
    // 3. 聚合汇总报告
}

// EvalReport.java
public class EvalReport {
    private String tenantId;
    private Instant evaluatedAt;
    private Summary summary;       // avgRecall, avgPrecision, hitRate, avgCorrectness...
    private List<Detail> details;  // 每条用例的详细结果
}
```

### API

```
POST /api/v1/eval/run?tenantId=default
  → 执行完整评测
  → 返回 EvalReport（汇总 + 明细）

GET /api/v1/eval/testcases?tenantId=default
  → 列出当前测试用例
```

## 非功能性设计

- **不新增持久化存储**：评测结果直接返回，暂不落库。需要时可追加写到 JSON 文件
- **测试用例走 YAML + git 管理**：方便版本追踪和团队协作
- **LLM Judge 温度 0**：保证评分可复现
- **独立 Judge 调用**：与评测目标链路隔离，防止串联偏差
- **错误隔离**：单条用例失败不影响后续用例

## 后续扩展点

- 评测结果持久化 + 历史趋势
- 自动生成评估数据集（从真实对话采样）
- 对比评测（A/B 测试不同 chunk 策略/embedding 模型）
