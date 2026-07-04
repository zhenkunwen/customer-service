package com.cs.customerservice.api.controller;

import com.cs.customerservice.application.eval.EvalConfig;
import com.cs.customerservice.application.eval.EvalDatasetGenerator;
import com.cs.customerservice.application.eval.EvalReport;
import com.cs.customerservice.application.eval.EvalService;
import com.cs.customerservice.application.eval.EvalTestCase;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@RestController
@RequestMapping("/api/v1/eval")
public class EvalController {

    private final EvalService evalService;
    private final EvalDatasetGenerator evalDatasetGenerator;

    public EvalController(EvalService evalService,
                          EvalDatasetGenerator evalDatasetGenerator) {
        this.evalService = evalService;
        this.evalDatasetGenerator = evalDatasetGenerator;
    }

    @PostMapping("/run")
    public Mono<EvalReport> runEvaluation(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(required = false) Integer topK) {
        if (topK != null) {
            return evalService.runEvaluation(tenantId, new EvalConfig(topK));
        }
        return evalService.runEvaluation(tenantId);
    }

    @GetMapping("/testcases")
    public Mono<ResponseEntity<List<EvalTestCase>>> listTestCases(
            @RequestParam(defaultValue = "default") String tenantId) {
        return Mono.fromCallable(() -> evalService.loadTestCases())
                .subscribeOn(Schedulers.boundedElastic())
                .map(all -> {
                    List<EvalTestCase> filtered = all.stream()
                            .filter(tc -> tenantId.equals(tc.getTenantId()))
                            .toList();
                    return ResponseEntity.ok(filtered);
                });
    }

    @GetMapping("/history")
    public Mono<Page<EvalReport>> getHistory(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return evalService.getHistory(tenantId, page, size);
    }

    @GetMapping("/history/{id}")
    public Mono<ResponseEntity<EvalReport>> getReport(@PathVariable Long id) {
        return evalService.getReport(id)
                .map(report -> report != null
                        ? ResponseEntity.ok(report)
                        : ResponseEntity.notFound().build());
    }

    @PostMapping("/generate-testcases")
    public Mono<ResponseEntity<String>> generateTestCases(
            @RequestParam(defaultValue = "default") String tenantId,
            @RequestParam(defaultValue = "5") int count) {
        return evalDatasetGenerator.generate(tenantId, count)
                .map(added -> ResponseEntity.ok("Generated " + added + " test cases"));
    }

    @GetMapping("/compare")
    public Mono<ResponseEntity<List<EvalReport>>> compare(
            @RequestParam long idA,
            @RequestParam long idB) {
        return Mono.zip(evalService.getReport(idA), evalService.getReport(idB))
                .map(tuple -> {
                    EvalReport a = tuple.getT1();
                    EvalReport b = tuple.getT2();
                    if (a == null || b == null) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok(List.of(a, b));
                });
    }
}
