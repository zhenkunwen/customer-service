# 向量知识库 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Elasticsearch 向量检索 + DeepSeek Embedding API 替换内存知识库

**Architecture:** DeepSeekEmbeddingService 将文本转为 1024 维向量，EsKnowledgeAdapter 实现 KnowledgeRetrievalPort 接口，在 ES 中做 knn 语义检索 + tenantId 过滤。保留 MemoryKnowledgeAdapter 作为 ES 不可用时的降级备选。

**Tech Stack:** Java 17 + Elasticsearch 8.x + DeepSeek Embedding API + elasticsearch-java client

---

### Task 1: 添加 ES 依赖 + Docker Compose

**Files:**
- Modify: `backend/pom.xml`
- Create: `docker-compose.es.yml`

- [ ] **Step 1: pom.xml 添加 elasticsearch-java 依赖**

```xml
<!-- pom.xml: 在 </dependencies> 前添加 -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.12.0</version>
</dependency>
```

```xml
<!-- 需要 jackson 反序列化，依赖已有 spring-boot-starter-webflux 自带的 jackson，无需额外加 -->
```

- [ ] **Step 2: 创建 docker-compose.es.yml**

```yaml
version: "3.9"
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.0
    container_name: cs-es
    restart: unless-stopped
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data
    networks:
      - cs-net

volumes:
  es-data:

networks:
  cs-net:
    driver: bridge
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 2: 创建 DeepSeekEmbeddingService

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/ai/DeepSeekEmbeddingService.java`

- [ ] **Step 1: 创建 Embedding Service**

```java
package com.cs.customerservice.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeepSeekEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekEmbeddingService.class);

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    /** 简单 LRU 缓存，避免对相同文本重复调用 API */
    private final LinkedHashMap<String, List<Float>> cache;

    public DeepSeekEmbeddingService(
            @Value("${spring.ai.openai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${cs.knowledge.embedding-model:text-embedding-v2}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl + "/v1/embeddings")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.cache = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                return size() > 1000;
            }
        };
    }

    public Mono<List<Float>> embed(String text) {
        // 缓存命中
        List<Float> cached = cache.get(text);
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.post()
                .bodyValue(Map.of("model", model, "input", text))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(5))
                .map(node -> {
                    List<Float> vector = new ArrayList<>();
                    JsonNode embedding = node.get("data").get(0).get("embedding");
                    embedding.forEach(v -> vector.add(v.floatValue()));
                    cache.put(text, vector);
                    return vector;
                })
                .onErrorResume(e -> {
                    log.warn("Embedding API call failed: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 清理缓存（知识条目更新时调用） */
    public void evict(String text) {
        cache.remove(text);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 3: 创建 EsKnowledgeAdapter

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/adapter/EsKnowledgeAdapter.java`
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/config/ElasticsearchConfig.java`
- Modify: `backend/src/main/java/com/cs/customerservice/infrastructure/adapter/MemoryKnowledgeAdapter.java` (添加 `@ConditionalOnMissingBean` 降级)

- [ ] **Step 1: 创建 ES 配置类**

```java
package com.cs.customerservice.infrastructure.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(value = "cs.knowledge.es.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    @Value("${cs.knowledge.es.host:localhost}")
    private String host;

    @Value("${cs.knowledge.es.port:9200}")
    private int port;

    @Value("${cs.knowledge.es.protocol:http}")
    private String protocol;

    @Bean
    @ConditionalOnProperty(value = "cs.knowledge.es.enabled", havingValue = "true", matchIfMissing = true)
    public ElasticsearchClient elasticsearchClient() {
        log.info("Connecting to Elasticsearch at {}://{}:{}", protocol, host, port);
        RestClient restClient = RestClient.builder(
                new HttpHost(host, port, protocol)
        ).build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
```

- [ ] **Step 2: 创建 EsKnowledgeAdapter**

```java
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
                                .numberOfShards(1)
                                .numberOfReplicas(0))
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
                .flatMap(vector -> doSearch(tenantId, vector, topK))
                .switchIfEmpty(Mono.just(List.of()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<List<KnowledgeChunk>> doSearch(String tenantId, List<Float> vector, int topK) {
        return Mono.fromCallable(() -> {
            try {
                SearchResponse<EsKnowledgeDoc> response = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .knn(k -> k
                                .field("embedding")
                                .queryVector(vector)
                                .k(topK)
                                .numCandidates(topK * 2))
                        .query(q -> q
                                .bool(b -> b
                                        .filter(f -> f
                                                .term(t -> t
                                                        .field("tenantId")
                                                        .value(tenantId))))),
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
                log.warn("ES search failed (tenantId={}): {}", tenantId, e.getMessage());
                return List.of();
            }
        });
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
                return List.of();
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
```

- [ ] **Step 3: 创建 EsKnowledgeDoc 内部模型类**

```java
// 放在 EsKnowledgeAdapter.java 同包下或作为内部类
// 为清晰，放在 domain 包
```

Create `backend/src/main/java/com/cs/customerservice/infrastructure/adapter/EsKnowledgeDoc.java`:

```java
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
}
```

- [ ] **Step 4: 修改 MemoryKnowledgeAdapter 加上降级条件**

```java
// 在 @Component 下加一行 @ConditionalOnMissingBean
// 当 EsKnowledgeAdapter 不存在时才启用 MemoryKnowledgeAdapter
```

Add to `MemoryKnowledgeAdapter.java`:

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

// 修改 @Component 为：
@Component
@ConditionalOnMissingBean(EsKnowledgeAdapter.class)
public class MemoryKnowledgeAdapter implements KnowledgeRetrievalPort {
```

- [ ] **Step 5: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 4: 创建 KnowledgeController

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/api/controller/KnowledgeController.java`

- [ ] **Step 1: 创建知识库管理 API**

```java
package com.cs.customerservice.api.controller;

import com.cs.customerservice.domain.KnowledgeChunk;
import com.cs.customerservice.infrastructure.adapter.EsKnowledgeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/knowledge")
@ConditionalOnBean(EsKnowledgeAdapter.class)
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);
    private final EsKnowledgeAdapter esAdapter;

    public KnowledgeController(EsKnowledgeAdapter esAdapter) {
        this.esAdapter = esAdapter;
    }

    @PostMapping
    public Mono<ResponseEntity<Map<String, Object>>> create(@RequestBody KnowledgeChunk chunk) {
        return esAdapter.save(chunk)
                .thenReturn(ResponseEntity.ok(Map.of("success", true, "title", chunk.getTitle())));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> delete(@PathVariable String id) {
        return esAdapter.delete(id)
                .thenReturn(ResponseEntity.ok(Map.of("success", true)));
    }

    @GetMapping
    public Mono<List<KnowledgeChunk>> list(@RequestParam(defaultValue = "default") String tenantId) {
        return esAdapter.listByTenant(tenantId);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 5: 创建 EsKnowledgeInitializer（启动数据迁移）

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/infrastructure/config/EsKnowledgeInitializer.java`

- [ ] **Step 1: 创建启动初始化器**

```java
package com.cs.customerservice.infrastructure.config;

import com.cs.customerservice.infrastructure.adapter.EsKnowledgeAdapter;
import com.cs.customerservice.domain.KnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnBean(EsKnowledgeAdapter.class)
public class EsKnowledgeInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EsKnowledgeInitializer.class);

    private final EsKnowledgeAdapter esAdapter;

    public EsKnowledgeInitializer(EsKnowledgeAdapter esAdapter) {
        this.esAdapter = esAdapter;
    }

    @Override
    public void run(String... args) {
        esAdapter.listByTenant("default")
                .filter(List::isEmpty)
                .flatMap(ignored -> {
                    log.info("ES knowledge base empty, seeding initial FAQ data...");
                    return migrateAll(seedData());
                })
                .block();
    }

    private List<KnowledgeChunk> seedData() {
        return List.of(
                KnowledgeChunk.builder().id("faq-1").tenantId("default").title("退货政策")
                        .content("7天内可无理由退货，15天内可换货。退货需保持商品完好，附购买凭证。").score(0.95).build(),
                KnowledgeChunk.builder().id("faq-2").tenantId("default").title("物流查询")
                        .content("登录APP进入「我的订单」可查看物流轨迹，或联系在线客服提供订单号查询。").score(0.90).build(),
                KnowledgeChunk.builder().id("faq-3").tenantId("default").title("优惠券使用")
                        .content("优惠券在结算页面选择使用，每笔订单限用一张，不可与其他活动叠加。").score(0.85).build(),
                KnowledgeChunk.builder().id("faq-4").tenantId("default").title("会员权益")
                        .content("VIP会员享专属折扣、免运费、优先客服通道。月卡30元，年卡299元。").score(0.80).build(),
                KnowledgeChunk.builder().id("faq-a1").tenantId("tenant-a").title("Tenant-A 专属退货")
                        .content("Tenant-A 用户享有14天无理由退货权益，含上门取件服务。").score(0.95).build(),
                KnowledgeChunk.builder().id("faq-b1").tenantId("tenant-b").title("Tenant-B 专属客服")
                        .content("Tenant-B 提供7x24小时专属客服热线：400-xxx-xxxx。").score(0.95).build()
        );
    }

    private Mono<Void> migrateAll(List<KnowledgeChunk> chunks) {
        List<Mono<Void>> saves = chunks.stream()
                .map(chunk -> esAdapter.save(chunk))
                .toList();
        return Mono.when(saves)
                .doOnSuccess(v -> log.info("Seeded {} knowledge entries to ES", chunks.size()));
    }
}
```

（不需要给 MemoryKnowledgeAdapter 加 getFaqStore 了，初始化器直接用内联种子数据。）

- [ ] **Step 2: 编译验证 + 启动验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 6: 添加 application.yml ES 配置

**Files:**
- Modify: `backend/src/main/resources/application.yml`

- [ ] **Step 1: 在 application.yml 添加 ES 配置段**

```yaml
# 在 cs.security 之后添加
  knowledge:
    es:
      enabled: ${ES_KNOWLEDGE_ENABLED:true}
      host: ${ES_HOST:localhost}
      port: ${ES_PORT:9200}
      protocol: ${ES_PROTOCOL:http}
    embedding-model: text-embedding-v2
```

- [ ] **Step 2: 启动验证**

```bash
# 启动 ES
docker compose -f docker-compose.es.yml up -d
# 等待 ES 就绪后启动 app
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

Expected: 启动日志显示 "Created ES index: knowledge" 和 "Migrated N knowledge entries to ES"
