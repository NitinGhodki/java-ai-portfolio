package com.aiportfolio.week_b.benchmark;

import com.aiportfolio.week_a.Day2.rag.DocumentIngestionService;
import com.aiportfolio.week_a.Day2.rag.RagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.*;

/**
 * RagBenchmarkController — measures Java RAG performance.
 * Run this AFTER running python_rag_benchmark.py.
 * Compare numbers directly — same task, same questions.
 */
@Slf4j
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class RagBenchmarkController {

    private final RagPipeline ragPipeline;
    private final DocumentIngestionService ingestionService;

    private static final String DOCUMENT = """
            The Starter plan costs 999 rupees per month with 100 AI queries per day.
            The Professional plan costs 2999 rupees per month with unlimited queries and priority support.
            The Enterprise plan is custom priced with dedicated infrastructure.
            Refunds are available within 30 days for annual plans.
            Monthly plans can be cancelled anytime but are not eligible for partial refunds.
            All plans include a 14-day free trial with no credit card required.
            Rate limits are 10 requests per second for Starter, 50 for Professional.
            """;

    private static final List<String> QUESTIONS = List.of(
            "What does the Professional plan cost?",
            "What is the refund policy for monthly plans?",
            "How long is the free trial?",
//            "What are the API rate limits for the Starter plan?",
            "Is there a free trial available?"
    );

    /**
     * POST /api/benchmark/rag
     * Run 10 RAG queries and return latency statistics.
     *
     * curl -X POST http://localhost:8080/api/benchmark/rag
     */
    @PostMapping("/rag")
    public ResponseEntity<?> runBenchmark() {
        log.info("[Benchmark] Starting Java RAG benchmark");

        // Ingest the same document as Python benchmark
        long ingestStart = System.currentTimeMillis();
        int chunks = ingestionService.ingestText(DOCUMENT, "benchmark_doc.txt");
        long ingestMs = System.currentTimeMillis() - ingestStart;

        // Memory before queries
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long heapBefore = memBean.getHeapMemoryUsage().getUsed();

        // Run 10 queries (same as Python — 5 questions × 2)
        List<Long> latencies = new ArrayList<>();
        List<String> answers = new ArrayList<>();

        List<String> tenQuestions = new ArrayList<>();
        for (int i = 0; i < 2; i++) tenQuestions.addAll(QUESTIONS);

        for (int i = 0; i < tenQuestions.size(); i++) {
            String question = tenQuestions.get(i);
            long start = System.currentTimeMillis();
            var response = ragPipeline.queryManual(question, 3);
            long latencyMs = System.currentTimeMillis() - start;
            latencies.add(latencyMs);
            answers.add(response.answer().substring(0, Math.min(50, response.answer().length())));
            log.info("[Benchmark] Query {}: {}ms — {}...", i + 1, latencyMs, response.answer().substring(0, Math.min(40, response.answer().length())));
        }

        // Memory after queries
        long heapAfter = memBean.getHeapMemoryUsage().getUsed();
        long heapMax = memBean.getHeapMemoryUsage().getMax();

        // Calculate statistics
        Collections.sort(latencies);
        long p50 = latencies.get(latencies.size() / 2);
        long p99 = latencies.get((int)(latencies.size() * 0.99) - 1);
        long min = Collections.min(latencies);
        long max = Collections.max(latencies);
        double avg = latencies.stream().mapToLong(l -> l).average().orElse(0);

        // JVM startup time (approximate — JVM was already up when Spring started)
        long jvmUptime = ManagementFactory.getRuntimeMXBean().getUptime();

        // Lines of code (counted manually)
        Map<String, Integer> javaLoc = Map.of(
                "VectorStoreConfig", 15,
                "DocumentIngestionService", 45,
                "RagPipeline_queryManual", 35,
                "RagController", 40,
                "total_rag_pipeline", 135
        );


        return ResponseEntity.ok(Map.of(
                "language + framework", "java + Spring AI + Spring Boot 3.3 + Spring AI 1.0",
                "jvmUptimeMs", jvmUptime,
                "ingestTimeMs", ingestMs,
                "chunksCreated", chunks,
                "memoryBeforeQueryMb",
                String.format("%.1f", heapBefore / 1024.0 / 1024.0),
                "memoryAfterQueryMb",
                String.format("%.1f", heapAfter / 1024.0 / 1024.0),
                "memoryMaxHeapMb",
                String.format("%.1f", heapMax / 1024.0 / 1024.0),
                "latencyStats", Map.of(
                        "p50Ms", p50,
                        "p99Ms", p99,
                        "minMs", min,
                        "maxMs", max,
                        "avgMs", Math.round(avg),
                        "allLatenciesMs", latencies
                ),
                "linesOfCode", javaLoc,
                "answers", answers
        ));
    }

    /**
     * GET /api/benchmark/loc
     * Lines of code comparison — Java vs Python for same RAG features.
     *
     * curl http://localhost:8080/api/benchmark/loc
     */
    @GetMapping("/loc")
    public ResponseEntity<?> linesOfCode() {
        return ResponseEntity.ok(Map.of(
                "feature", Map.of(
                        "basic_rag_pipeline", Map.of("python", 33, "java", 135),
                        "conversation_memory", Map.of("python", 25, "java", 45),
                        "streaming", Map.of("python", 8, "java", 12),
                        "structured_output", Map.of("python", 35, "java", 20),
                        "guardrails", Map.of("python", 40, "java", 25),
                        "rate_limiting", Map.of("python", 60, "java", 35),
                        "unit_tests", Map.of("python", 20, "java", 55),
                        "docker_deploy", Map.of("python", 15, "java", 20)
                ),
                "interpretation", Map.of(
                        "python_wins_at", List.of(
                                "RAG pipeline setup (3x less code)",
                                "Streaming (more concise)",
                                "Rapid prototyping"
                        ),
                        "java_wins_at", List.of(
                                "Structured extraction (return type = schema)",
                                "Guardrails (annotation-based, structural enforcement)",
                                "Rate limiting (Bucket4j integration)",
                                "Unit testing (WireMock, JUnit 5 ecosystem)",
                                "Enterprise integration (existing Spring Boot services)"
                        )
                )
        ));
    }
}