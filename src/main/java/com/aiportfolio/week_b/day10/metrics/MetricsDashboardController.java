package com.aiportfolio.week_b.day10.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MetricsDashboardController — human-readable metrics summary.
 *
 * /actuator/prometheus → Prometheus format (for Grafana scraping)
 * /api/metrics/summary → JSON summary (for humans reading the API)
 * /api/metrics/cost    → cost projection based on token usage
 */
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsDashboardController {

    private final AiMetricsService metricsService;

    /**
     * GET /api/metrics/summary
     * Human-readable metrics snapshot.
     *
     * curl http://localhost:8080/api/metrics/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<AiMetricsService.MetricSnapshot> summary() {
        return ResponseEntity.ok(metricsService.getSnapshot());
    }

    /**
     * GET /api/metrics/cost
     * Cost projection based on current usage.
     *
     * curl http://localhost:8080/api/metrics/cost
     */
    @GetMapping("/cost")
    public ResponseEntity<?> costProjection() {
        var snapshot = metricsService.getSnapshot();
        long apiCalls = snapshot.totalLlmCalls();
        double avgTokens = snapshot.avgTokensPerCall();

        double costSoFar = (apiCalls * avgTokens / 1000.0) * 0.0001;
        double projectedDaily = apiCalls > 0
                ? (costSoFar / Math.max(1, apiCalls)) * 10_000
                : 0;

        return ResponseEntity.ok(Map.of(
                "totalApiCalls", apiCalls,
                "avgTokensPerCall", Math.round(avgTokens),
                "costSoFarUsd", String.format("$%.6f", costSoFar),
                "projectedDailyCostAt10kQueries", String.format("$%.4f", projectedDaily),
                "cacheHitRate", String.format("%.1f%%", snapshot.cacheHitRate() * 100),
                "estimatedSavingsFromCache",
                String.format("$%.6f", snapshot.cacheHits() * avgTokens / 1000.0 * 0.0001),
                "ollamaHealthy", snapshot.ollamaHealthy(),
                "note", "Costs based on HuggingFace serverless pricing. Ollama calls are free."
        ));
    }
}
