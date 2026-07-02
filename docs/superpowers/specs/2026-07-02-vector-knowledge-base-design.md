# 向量知识库 — 设计文档

## 概要

将现有硬编码的内存知识库（`MemoryKnowledgeAdapter`）替换为 Elasticsearch 向量检索方案，使用 DeepSeek Embedding API 将文本转为向量，实现语义级知识匹配。

## 动机

当前 `MemoryKnowledgeAdapter` 仅有十几条硬编码 FAQ，按预设 score 排序，无法理解语义（"退货"≠"退换"）。随着业务知识增长，需要一个可持久化、支持语义检索、有管理界面的知识库系统。

## 架构

```
┌──────────────┐    ┌────────────────────┐    ┌───────────────┐
│  用户问题     │───→│ DeepSeekEmbedding   │───→│  Elasticsearch │
│               │    │ Service.embed(text) │    │  knn 检索      │
└──────────────┘    └────────────────────┘    └───────────────┘
                            │                          │
                            │ 返回 float[1024]         │ 返回匹配片段
                            ▼                          ▼
                     ┌──────────────────────────────────────┐
                     │  EsKnowledgeAdapter.search()          │
                     │  1. embed(query) → 向量               │
                     │  2. ES knn + tenantId filter          │
                     │  3. 返回 List<KnowledgeChunk>         │
                     └──────────────────────────────────────┘
                                    │
                                    ▼
                     CustomerChatOrchestrator.buildSystemPrompt()
```

## 新增组件

### 1. DeepSeekEmbeddingService

**职责：** 调用 DeepSeek Embedding API 将文本转为向量。

- 端点：`POST https://api.deepseek.com/v1/embeddings`
- 模型：`text-embedding-v2`（1024 维）
- 返回：`List<Float>`
- 缓存：对相同文本的 embedding 结果做内存缓存（LRU，最多 1000 条），避免重复调用
- 超时：5s

### 2. EsKnowledgeAdapter

**职责：** 实现 `KnowledgeRetrievalPort`，对接 Elasticsearch 做向量检索。

- 使用 `elasticsearch-java` 客户端
- 创建 `knowledge` 索引（自动初始化）
- `search(tenantId, query, topK)` 流程：
  1. `embeddingService.embed(query)` 获取向量
  2. ES `knn` 查询：`field=embedding, k=topK*2, numCandidates=100`
  3. `boolean filter` 精确过滤 `tenantId`
  4. 按 `_score` 降序取 topK
  5. 返回 `List<KnowledgeChunk>`

### 3. KnowledgeController

**职责：** 知识库管理 CRUD，仅主管（TEAM_LEAD）可操作。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `api/v1/knowledge` | 添加知识条目 |
| DELETE | `api/v1/knowledge/{id}` | 删除 |
| PUT | `api/v1/knowledge/{id}` | 更新 |
| GET | `api/v1/knowledge?tenantId=X` | 列表查询 |

### 4. EsKnowledgeInitializer

**职责：** 应用启动时，检查 ES 中是否已有数据，若无则将 `MemoryKnowledgeAdapter` 的存量 FAQ 迁移到 ES，并生成 embedding。

## ES 索引设计

```
索引名: knowledge

mappings:
  tenantId:     keyword
  title:        text (standard analyzer)
  content:      text (standard analyzer)
  embedding:    dense_vector, dims=1024, index=true, similarity=cosine
  score:        float

settings:
  number_of_shards:   1
  number_of_replicas: 0  (单节点)
```

## 数据流

### 写入流程
```
管理员 POST {title, content, tenantId, score}
  → EsKnowledgeAdapter.save()
  → embeddingService.embed(title + content)
  → ES index document with vector
```

### 查询流程
```
用户提问
  → CustomerChatOrchestrator
  → EsKnowledgeAdapter.search(tenantId, query, topK)
  → embeddingService.embed(query)
  → ES knn: query_vector + filter(tenantId)
  → 返回 topK 条 KnowledgeChunk
  → 注入 system prompt
```

## 依赖变更

```xml
<!-- 新增 -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
</dependency>
```

## 部署变更

新增 `docker-compose.yml` 服务定义：

```yaml
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: cs-es
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - cs-network
```

## 边界情况

| 场景 | 行为 |
|------|------|
| ES 不可用 | 返回空结果，打 warn 日志，不阻塞主流程 |
| Embedding API 超时 | 降级返回空，不影响对话 |
| 查询无结果 | 返回空列表，Orchestrator 走知识库未覆盖流程 → 转人工 |
| 多租户 | 每查询都带 tenantId filter，数据隔离 |
| 知识条目无向量 | 建索引时同步生成，更新时重新生成 |

## 测试场景

1. 启动服务 → ES 索引自动创建 → 旧数据迁移成功
2. 添加知识条目 → ES 中有数据
3. 语义搜索："退换"匹配到"退货政策"（语义匹配，非关键词）
4. 多租户隔离：tenant-a 搜不到 tenant-b 的数据
5. ES 挂掉时不影响对话主流程
