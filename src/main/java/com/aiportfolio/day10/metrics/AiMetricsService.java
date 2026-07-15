package com.aiportfolio.day10.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * AiMetricsService — centralised AI-specific metrics.
 *
 * All metrics are registered with Micrometer's MeterRegistry.
 * They automatically appear at /actuator/prometheus and /actuator/metrics.
 * Grafana scrapes /actuator/prometheus and displays them as charts.
 *
 * Python equivalent: your Day 11 Python Week 2 custom tracer.
 * Java difference: Micrometer is a framework standard, not custom code.
 * The metrics integrate with your entire Spring ecosystem automatically.
 *
 * Metric naming convention: all.lowercase.dot.separated
 * Tags: key-value pairs that let you slice data in Grafana
 *   e.g. ai.llm.calls with tag model=ollama vs model=huggingface
 *   → one metric, two lines on the chart, instant comparison
 */
@Slf4j
@Service
public class AiMetricsService {

    private final MeterRegistry registry;

    // Counters — increment on each event
    private final Counter llmCallsTotal;
    private final Counter cacheHitsTotal;
    private final Counter cacheMissesTotal;
    private final Counter guardrailBlocksTotal;
    private final Counter ragQueriesTotal;

    // Timers — measure latency automatically (p50, p95, p99 computed by Micrometer)
    private final Timer llmLatencyTimer;
    private final Timer ragLatencyTimer;
    private final Timer routingLatencyTimer;

    // Gauges — current values (backed by AtomicLong for thread safety)
    private final AtomicLong activeSessionsGauge = new AtomicLong(0);
    private final AtomicLong ollamaHealthStatus = new AtomicLong(0); // 1=up, 0=down

    // Distribution summary — for token counts (not time-based)
    private final DistributionSummary tokenUsageSummary;

    public AiMetricsService(MeterRegistry registry) {
        this.registry = registry;

        // Register counters
        llmCallsTotal = Counter.builder("ai.llm.calls.total")
                .description("Total number of LLM API calls")
                .register(registry);

        cacheHitsTotal = Counter.builder("ai.cache.hits.total")
                .description("Number of cache hits (LLM call avoided)")
                .register(registry);

        cacheMissesTotal = Counter.builder("ai.cache.misses.total")
                .description("Number of cache misses (LLM call required)")
                .register(registry);

        guardrailBlocksTotal = Counter.builder("ai.guardrail.blocks.total")
                .description("Number of requests blocked by guardrails")
                .register(registry);

        ragQueriesTotal = Counter.builder("ai.rag.queries.total")
                .description("Total RAG queries processed")
                .register(registry);

        // Register timers
        llmLatencyTimer = Timer.builder("ai.llm.latency")
                .description("LLM call latency in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        ragLatencyTimer = Timer.builder("ai.rag.latency")
                .description("Full RAG pipeline latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        routingLatencyTimer = Timer.builder("ai.router.latency")
                .description("Model routing decision latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // Register gauge (reads AtomicLong value on each scrape)
        Gauge.builder("ai.sessions.active", activeSessionsGauge, AtomicLong::get)
                .description("Number of active chat sessions")
                .register(registry);

        Gauge.builder("ai.ollama.health", ollamaHealthStatus, AtomicLong::get)
                .description("Ollama local model health: 1=up, 0=down")
                .register(registry);

        // Token usage distribution
        tokenUsageSummary = DistributionSummary.builder("ai.tokens.used")
                .description("Token count per LLM call")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        log.info("[Metrics] AI metrics registered with Micrometer");
    }

    /**
     * Record an LLM call with tagged metrics.
     * Tags allow slicing by model in Grafana:
     *   ai.llm.calls.total{model="ollama"} = 500
     *   ai.llm.calls.total{model="huggingface"} = 200
     */
    public void recordLlmCall(String model, long latencyMs, int tokenCount) {
        // Tagged counter — one counter, multiple tag values
        registry.counter("ai.llm.calls", "model", model).increment();
        llmCallsTotal.increment();

        // Record latency sample (Micrometer computes percentiles)
        llmLatencyTimer.record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        // Record token distribution
        tokenUsageSummary.record(tokenCount);

        log.debug("[Metrics] LLM call: model={} latency={}ms tokens={}", model, latencyMs, tokenCount);
    }

    /**
     * Time a block of code and record as RAG latency.
     * Usage: ragResult = metrics.timeRag(() -> pipeline.query(question, topK));
     */
    public <T> T timeRag(Supplier<T> ragCall) {
        ragQueriesTotal.increment();
        return ragLatencyTimer.record(ragCall);
    }

    /**
     * Time routing decision.
     */
    public <T> T timeRouting(Supplier<T> routingCall) {
        return routingLatencyTimer.record(routingCall);
    }

    public void recordCacheHit() {
        cacheHitsTotal.increment();
        registry.counter("ai.cache.result", "result", "hit").increment();
    }

    public void recordCacheMiss() {
        cacheMissesTotal.increment();
        registry.counter("ai.cache.result", "result", "miss").increment();
    }

    public void recordGuardrailBlock(String guardrailType) {
        guardrailBlocksTotal.increment();
        registry.counter("ai.guardrail.blocks", "type", guardrailType).increment();
        log.warn("[Metrics] Guardrail block recorded: type={}", guardrailType);
    }

    public void recordRoutingDecision(String complexity, String model) {
        registry.counter("ai.router.decisions",
                "complexity", complexity,
                "model", model
        ).increment();
    }

    public void setActiveSessions(long count) {
        activeSessionsGauge.set(count);
    }

    public void setOllamaHealth(boolean isHealthy) {
        ollamaHealthStatus.set(isHealthy ? 1 : 0);
    }

    /**
     * Get current metric snapshot for the dashboard endpoint.
     */
    public MetricSnapshot getSnapshot() {
        double cacheHitRate = 0.0;
        double totalCacheRequests = cacheHitsTotal.count() + cacheMissesTotal.count();
        if (totalCacheRequests > 0) {
            cacheHitRate = cacheHitsTotal.count() / totalCacheRequests;
        }

        return new MetricSnapshot(
                (long) llmCallsTotal.count(),
                (long) ragQueriesTotal.count(),
                (long) guardrailBlocksTotal.count(),
                (long) cacheHitsTotal.count(),
                (long) cacheMissesTotal.count(),
                cacheHitRate,
                llmLatencyTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                ragLatencyTimer.mean(java.util.concurrent.TimeUnit.MILLISECONDS),
                tokenUsageSummary.mean(),
                activeSessionsGauge.get(),
                ollamaHealthStatus.get() == 1
        );
    }

    public record MetricSnapshot(
            long totalLlmCalls,
            long totalRagQueries,
            long guardrailBlockCount,
            long cacheHits,
            long cacheMisses,
            double cacheHitRate,
            double avgLlmLatencyMs,
            double avgRagLatencyMs,
            double avgTokensPerCall,
            long activeSessions,
            boolean ollamaHealthy
    ) {}
}