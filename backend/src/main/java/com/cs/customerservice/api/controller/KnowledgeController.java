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
