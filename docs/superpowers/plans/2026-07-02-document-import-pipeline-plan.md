# 文档批量导入管线 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 支持 PDF/Word/TXT 批量上传，经三段式切分、去重、元数据标注后写入 ES 知识库

**Architecture:** DocumentParser 解析原文 → SmartChunker 三段切分（语义边界→长度归一→重叠+元数据）→ BatchImportService 去重编排 → EsKnowledgeAdapter 写入 ES。所有切分配置由 ChunkConfig 统一管理。

**Tech Stack:** Java 17 + Apache PDFBox 3.0.1 + Apache POI 5.2.5 + Elasticsearch 8.x

---

### Task 1: 数据模型扩展 + 依赖添加

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/java/com/cs/customerservice/domain/KnowledgeChunk.java`
- Modify: `backend/src/main/java/com/cs/customerservice/infrastructure/adapter/EsKnowledgeDoc.java`
- Modify: `backend/src/main/java/com/cs/customerservice/infrastructure/config/EsKnowledgeInitializer.java`

- [ ] **Step 1: pom.xml 添加 PDFBox + POI 依赖**

在 `</dependencies>` 前添加：

```xml
<!-- PDF parsing -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>
<!-- Word (docx) parsing -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

- [ ] **Step 2: KnowledgeChunk 添加元数据字段**

```java
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
```

- [ ] **Step 3: EsKnowledgeDoc 同步添加元数据字段**

```java
public class EsKnowledgeDoc {
    private String id;
    private String tenantId;
    private String title;
    private String content;
    private double score;
    private List<Float> embedding;

    // 同步添加
    private String source;
    private String section;
    private int chunkIndex;
    private int totalChunks;
    private Integer pageNum;
    private String docType;
}
```

- [ ] **Step 4: 更新 EsKnowledgeInitializer 种子数据**

给每条 seed data 加上元数据，如：

```java
KnowledgeChunk.builder()
    .id("faq-1").tenantId("default")
    .title("退货政策")
    .content("7天内可无理由退货，15天内可换货...")
    .score(0.95)
    .source("seed-data")
    .section("退货政策")
    .chunkIndex(0).totalChunks(1)
    .docType("policy")
    .build();
```

其它 5 条同理补上。

- [ ] **Step 5: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 2: ChunkConfig 切分配置

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/knowledge/ChunkConfig.java`

- [ ] **Step 1: 创建切分配置类**

```java
package com.cs.customerservice.application.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "cs.knowledge.chunk")
public class ChunkConfig {

    /** 按文档类型配置切分策略 */
    private Map<String, Strategy> strategies = Map.of(
            "tech",       new Strategy(300, 800, 80),
            "narrative",  new Strategy(500, 1200, 120),
            "policy",     new Strategy(400, 1500, 100)
    );

    /** 默认策略（未匹配 docType 时使用） */
    private Strategy fallback = new Strategy(400, 1000, 100);

    public Strategy getStrategy(String docType) {
        return strategies.getOrDefault(docType, fallback);
    }

    public record Strategy(int minSize, int maxSize, int overlap) {}

    // getters / setters
    public Map<String, Strategy> getStrategies() { return strategies; }
    public void setStrategies(Map<String, Strategy> strategies) { this.strategies = strategies; }
    public Strategy getFallback() { return fallback; }
    public void setFallback(Strategy fallback) { this.fallback = fallback; }
}
```

- [ ] **Step 2: application.yml 添加切分配置**

在 `cs.knowledge` 下添加：

```yaml
  chunk:
    strategies:
      tech:
        min-size: 300
        max-size: 800
        overlap: 80
      narrative:
        min-size: 500
        max-size: 1200
        overlap: 120
      policy:
        min-size: 400
        max-size: 1500
        overlap: 100
    fallback:
      min-size: 400
      max-size: 1000
      overlap: 100
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

---

### Task 3: DocumentParser 文档解析

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/knowledge/DocumentParser.java`

- [ ] **Step 1: 创建文档解析器**

```java
package com.cs.customerservice.application.knowledge;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    public ParseResult parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) filename = "unknown";

        String lower = filename.toLowerCase();
        String text;
        int totalPages = 0;

        if (lower.endsWith(".pdf")) {
            ParseResult result = parsePdf(file.getInputStream());
            text = result.text();
            totalPages = result.totalPages();
        } else if (lower.endsWith(".docx")) {
            text = parseDocx(file.getInputStream());
        } else {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        return new ParseResult(text, filename, totalPages);
    }

    private ParseResult parsePdf(InputStream in) throws IOException {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            return new ParseResult(text, "pdf", doc.getNumberOfPages());
        }
    }

    private String parseDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
            return extractor.getText();
        }
    }

    public record ParseResult(String text, String sourceName, int totalPages) {}
    public record ParseResultWithPages(String text, String sourceName, int totalPages) {
        public ParseResultWithPages { if (totalPages < 0) totalPages = 0; }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

---

### Task 4: SmartChunker 三段式切分器

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/knowledge/SmartChunker.java`

- [ ] **Step 1: 创建切分器**

```java
package com.cs.customerservice.application.knowledge;

import com.cs.customerservice.domain.KnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SmartChunker {

    private static final Logger log = LoggerFactory.getLogger(SmartChunker.class);

    // 语义边界模式（按优先级排列）
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "(?m)^[#]{1,6}\\s+.*$"                                    // Markdown: # ##
            + "|^(?:第[一二三四五六七八九十百千]+[章节条篇]\\s*.*)$"    // 第X章/节/条/篇
            + "|^(?:[一二三四五六七八九十]+[、]\\s*.*)$"                // 一、二、三、
            + "|^(?:（[一二三四五六七八九十]+）\\s*.*)$"               // （一）（二）
            + "|^(?:\\d+\\.\\s*.*)$"                                   // 1. 2. 3.
    );

    private final ChunkConfig chunkConfig;

    public SmartChunker(ChunkConfig chunkConfig) {
        this.chunkConfig = chunkConfig;
    }

    /**
     * 三段式切分：语义边界 → 长度归一 → 重叠+元数据
     * @param text      原始文本
     * @param source    来源文件名
     * @param docType   文档类型（tech/narrative/policy）
     * @param totalPages PDF 总页码（非 PDF 传 0）
     * @return 知识块列表
     */
    public List<KnowledgeChunk> chunk(String text, String source, String docType, int totalPages) {
        ChunkConfig.Strategy strategy = chunkConfig.getStrategy(docType);

        // Stage 1: 语义边界切分
        List<SectionBlock> sections = splitBySemanticBoundaries(text);

        // Stage 2: 长度归一化（合并过小、切分过大）
        List<SectionBlock> normalized = normalizeLength(sections, strategy);

        // Stage 3: 重叠 + 元数据
        return addOverlapAndMetadata(normalized, strategy, source, docType, totalPages);
    }

    // ========== Stage 1: 语义边界切分 ==========

    static List<SectionBlock> splitBySemanticBoundaries(String text) {
        List<SectionBlock> blocks = new ArrayList<>();
        List<SectionBoundary> boundaries = new ArrayList<>();

        // 找出所有标题/章节边界
        Matcher m = SECTION_PATTERN.matcher(text);
        while (m.find()) {
            boundaries.add(new SectionBoundary(m.start(), m.end(), m.group().trim()));
        }

        if (boundaries.isEmpty()) {
            // 没有找到章节结构，按双换行切
            String[] parts = text.split("\\n\\s*\\n");
            for (int i = 0; i < parts.length; i++) {
                blocks.add(new SectionBlock(parts[i].trim(), "段落" + (i + 1)));
            }
            return blocks;
        }

        // 按边界切分
        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i).endPos;
            int end = (i + 1 < boundaries.size()) ? boundaries.get(i + 1).startPos : text.length();
            String sectionTitle = boundaries.get(i).title;
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                blocks.add(new SectionBlock(content, sectionTitle));
            }
        }

        return blocks;
    }

    // ========== Stage 2: 长度归一化 ==========

    static List<SectionBlock> normalizeLength(List<SectionBlock> sections, ChunkConfig.Strategy strategy) {
        List<SectionBlock> result = new ArrayList<>();
        SectionBlock pending = null;

        for (SectionBlock block : sections) {
            if (block.content.length() < strategy.minSize()) {
                // 太小：尝试合并到 pending
                if (pending == null) {
                    pending = block;
                } else if (pending.content.length() + block.content.length() <= strategy.maxSize()) {
                    pending = new SectionBlock(
                            pending.content + "\n" + block.content,
                            pending.sectionTitle
                    );
                } else {
                    result.add(pending);
                    pending = block;
                }
            } else if (block.content.length() <= strategy.maxSize()) {
                // 正好：直接产出
                if (pending != null) {
                    result.add(pending);
                    pending = null;
                }
                result.add(block);
            } else {
                // 太大：在句子边界切分
                if (pending != null) {
                    result.add(pending);
                    pending = null;
                }
                result.addAll(splitOversize(block, strategy.maxSize()));
            }
        }
        if (pending != null) {
            result.add(pending);
        }

        return result;
    }

    static List<SectionBlock> splitOversize(SectionBlock block, int maxSize) {
        List<SectionBlock> parts = new ArrayList<>();
        String remaining = block.content;
        while (remaining.length() > maxSize) {
            int breakAt = findBreakPoint(remaining, maxSize);
            parts.add(new SectionBlock(remaining.substring(0, breakAt).trim(), block.sectionTitle));
            remaining = remaining.substring(breakAt).trim();
        }
        if (!remaining.isEmpty()) {
            parts.add(new SectionBlock(remaining, block.sectionTitle));
        }
        return parts;
    }

    static int findBreakPoint(String text, int around) {
        for (int i = around; i > Math.max(around - 100, 0); i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？' || c == '；') {
                return i + 1;
            }
        }
        for (int i = around; i < Math.min(around + 50, text.length()); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '\n' || c == '！' || c == '？') {
                return i + 1;
            }
        }
        return around; // 找不到就硬切
    }

    // ========== Stage 3: 重叠 + 元数据 ==========

    List<KnowledgeChunk> addOverlapAndMetadata(
            List<SectionBlock> blocks, ChunkConfig.Strategy strategy,
            String source, String docType, int totalPages) {

        List<KnowledgeChunk> result = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            String content = blocks.get(i).content;

            // 不是第一块：加上上一块尾部 overlap 字作为上下文
            if (i > 0) {
                String prevTail = blocks.get(i - 1).content;
                String overlap = prevTail.substring(
                        Math.max(0, prevTail.length() - strategy.overlap()));
                content = overlap + "\n" + content;
            }

            String id = UUID.randomUUID().toString();
            result.add(KnowledgeChunk.builder()
                    .id(id)
                    .tenantId("default")
                    .title(blocks.get(i).sectionTitle)
                    .content(content)
                    .score(0.9)
                    .source(source)
                    .section(blocks.get(i).sectionTitle)
                    .chunkIndex(i)
                    .totalChunks(blocks.size())
                    .pageNum(null) // 精确页码需 PDF 坐标计算，暂缺
                    .docType(docType)
                    .build());
        }

        return result;
    }

    // ========== 内部数据结构 ==========

    record SectionBlock(String content, String sectionTitle) {}
    record SectionBoundary(int startPos, int endPos, String title) {}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

---

### Task 5: BatchImportService 批量导入编排

**Files:**
- Create: `backend/src/main/java/com/cs/customerservice/application/knowledge/BatchImportService.java`

- [ ] **Step 1: 创建批量导入服务**

```java
package com.cs.customerservice.application.knowledge;

import com.cs.customerservice.domain.KnowledgeChunk;
import com.cs.customerservice.infrastructure.adapter.EsKnowledgeAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class BatchImportService {

    private static final Logger log = LoggerFactory.getLogger(BatchImportService.class);

    private final DocumentParser documentParser;
    private final SmartChunker smartChunker;
    private final EsKnowledgeAdapter esAdapter;

    public BatchImportService(DocumentParser documentParser, SmartChunker smartChunker,
                              EsKnowledgeAdapter esAdapter) {
        this.documentParser = documentParser;
        this.smartChunker = smartChunker;
        this.esAdapter = esAdapter;
    }

    public Mono<BatchResult> importFile(MultipartFile file, String tenantId, String docType) {
        return Mono.fromCallable(() -> {
            // 1. 解析
            DocumentParser.ParseResult parsed = documentParser.parse(file);
            log.info("Parsed: source={}, length={}, pages={}",
                    parsed.sourceName(), parsed.text().length(), parsed.totalPages());

            // 2. 切分
            List<KnowledgeChunk> chunks = smartChunker.chunk(
                    parsed.text(), parsed.sourceName(), docType, parsed.totalPages());

            // 3. 去重 + 写入（保留重复计数）
            int inserted = 0, duplicates = 0;
            for (KnowledgeChunk chunk : chunks) {
                chunk.setTenantId(tenantId != null ? tenantId : "default");
                if (isDuplicate(chunk)) {
                    duplicates++;
                    continue;
                }
                esAdapter.save(chunk).block();
                inserted++;
            }

            log.info("Import complete: source={}, chunks={}, inserted={}, duplicates={}",
                    parsed.sourceName(), chunks.size(), inserted, duplicates);

            return new BatchResult(parsed.sourceName(), chunks.size(), inserted, duplicates);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 去重检查：MD5(content) 是否已存在 */
    private boolean isDuplicate(KnowledgeChunk chunk) {
        String hash = md5(chunk.getContent());
        // 列出该租户所有知识，检查 content hash
        List<KnowledgeChunk> existing = esAdapter.listByTenant(chunk.getTenantId()).block();
        if (existing == null) return false;
        return existing.stream().anyMatch(k -> md5(k.getContent()).equals(hash));
    }

    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public record BatchResult(String source, int totalChunks, int inserted, int duplicates) {}
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

---

### Task 6: KnowledgeController 批量上传端点

**Files:**
- Modify: `backend/src/main/java/com/cs/customerservice/api/controller/KnowledgeController.java`

- [ ] **Step 1: 添加 batch 端点**

添加注入和端点：

```java
private final BatchImportService batchImportService;

// 构造器加参数（已有 esAdapter 参数不变）
public KnowledgeController(EsKnowledgeAdapter esAdapter,
                           BatchImportService batchImportService) {
    this.esAdapter = esAdapter;
    this.batchImportService = batchImportService;
}

@PostMapping("/batch")
public Mono<BatchImportService.BatchResult> batchUpload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(defaultValue = "default") String tenantId,
        @RequestParam(defaultValue = "policy") String docType) {
    return batchImportService.importFile(file, tenantId, docType);
}
```

需要新增 import：
```java
import com.cs.customerservice.application.knowledge.BatchImportService;
import org.springframework.web.multipart.MultipartFile;
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

---

### Task 7: 功能验证

- [ ] **Step 1: 启动 ES + 应用**

```bash
docker compose -f docker-compose.es.yml up -d
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

- [ ] **Step 2: 上传测试**

```bash
# 创建一个测试文档
echo "这是一段测试内容。第一章 安装说明。请先下载安装包。第二章 配置说明。修改配置文件。" > /tmp/test.txt

# 上传
curl -X POST "http://localhost:8080/api/v1/knowledge/batch" \
  -H "X-API-Key: change-me" \
  -F "file=@/tmp/test.txt" \
  -F "tenantId=default" \
  -F "docType=tech"
```

Expected: 返回 `{"source":"test.txt","totalChunks":N,"inserted":N,"duplicates":0}`

- [ ] **Step 3: 搜索验证**

```bash
# 通过 AI 聊天触发知识检索（问相关问题看能否召回）
curl -X POST "http://localhost:8080/api/v1/cs/chat" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -H "X-API-Key: change-me" \
  --data-binary '{"sessionId":"test","question":"怎么安装","tenantId":"default","userId":"u1"}'
```

Expected: 回答引用到刚上传的知识

- [ ] **Step 4: 清理测试数据**

```bash
# 清 ES 索引
curl -X DELETE http://localhost:9200/knowledge
```
