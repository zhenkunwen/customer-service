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
            log.info("Parsed: source={}, len={}, pages={}",
                    parsed.sourceName(), parsed.text().length(), parsed.totalPages());

            // 2. 切分
            List<KnowledgeChunk> chunks = smartChunker.chunk(
                    parsed.text(), parsed.sourceName(), docType, parsed.totalPages());

            // 3. 为每个 chunk 设置租户
            chunks.forEach(c -> c.setTenantId(tenantId != null ? tenantId : "default"));

            // 4. 去重 + 写入
            int inserted = 0, duplicates = 0;
            List<KnowledgeChunk> existing = esAdapter.listByTenant(chunks.get(0).getTenantId()).block();
            for (KnowledgeChunk chunk : chunks) {
                if (existing != null && existing.stream().anyMatch(e -> md5(e.getContent()).equals(md5(chunk.getContent())))) {
                    duplicates++;
                } else {
                    esAdapter.save(chunk).block();
                    inserted++;
                }
            }

            log.info("Import done: source={}, total={}, inserted={}, dup={}",
                    parsed.sourceName(), chunks.size(), inserted, duplicates);
            return new BatchResult(parsed.sourceName(), chunks.size(), inserted, duplicates);
        }).subscribeOn(Schedulers.boundedElastic());
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
