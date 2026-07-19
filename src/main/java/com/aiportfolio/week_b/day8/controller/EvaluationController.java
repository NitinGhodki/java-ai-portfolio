package com.aiportfolio.week_b.day8.controller;

import com.aiportfolio.week_b.day8.evaluation.RagEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * EvaluationController — REST API for RAG quality evaluation.
 *
 * Endpoints:
 * POST /api/eval/run         — run full evaluation
 * GET  /api/eval/dataset     — show the test dataset
 * GET  /api/eval/comparison  — Spring AI vs RAGAS comparison
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvaluationController {

    private final RagEvaluationService evaluationService;

    /**
     * POST /api/eval/run
     * Run the full evaluation suite.
     *
     * curl -X POST "http://localhost:8080/api/eval/run?topK=3"
     *
     * This makes multiple LLM calls (one per test sample + one per evaluation).
     * With 5 samples: ~10 LLM calls total. Takes 30-60 seconds.
     */
    @PostMapping("/run")
    public ResponseEntity<RagEvaluationService.EvaluationReport> runEvaluation(
            @RequestParam(defaultValue = "3") int topK) {
        log.info("[Eval Controller] Starting evaluation with topK={}", topK);
        var report = evaluationService.evaluate(topK);
        return ResponseEntity.ok(report);
    }

    /**
     * GET /api/eval/dataset
     * Show the test dataset without running evaluation.
     *
     * curl http://localhost:8080/api/eval/dataset
     */
    @GetMapping("/dataset")
    public ResponseEntity<?> getDataset() {
        return ResponseEntity.ok(Map.of(
                "totalSamples", RagEvaluationService.TEST_DATASET.size(),
                "samples", RagEvaluationService.TEST_DATASET
        ));
    }
}