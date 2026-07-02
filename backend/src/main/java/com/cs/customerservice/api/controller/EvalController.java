package com.cs.customerservice.api.controller;

import com.cs.customerservice.application.eval.EvalReport;
import com.cs.customerservice.application.eval.EvalService;
import com.cs.customerservice.application.eval.EvalTestCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
}
