package com.aiportfolio.week_b.day9.controller;

import com.aiportfolio.week_b.day9.benchmark.ModelBenchmark;
import com.aiportfolio.week_b.day9.ollama.OllamaService;
import com.aiportfolio.week_b.day9.router.ModelRouter;
import com.aiportfolio.week_b.day9.router.QueryClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/router")
@RequiredArgsConstructor
public class RouterController {

    private final ModelRouter modelRouter;
    private final OllamaService ollamaService;
    private final ModelBenchmark modelBenchmark;

    public record QueryRequest(String query) {}

    /**
     * POST /api/router/query
     * Route the query to the right model automatically.
     *
     * Test with simple: "What is the refund policy price?"
     * Test with complex: "Compare all plans, calculate annual costs with 20% discount, and recommend the best for a 10-person startup with technical needs."
     *
     * curl -X POST http://localhost:8080/api/router/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"query": "What is the Starter plan price?"}'
     */
    @PostMapping("/query")
    public ResponseEntity<ModelRouter.RouterResponse> route(@RequestBody QueryRequest req) {
        return ResponseEntity.ok(modelRouter.route(req.query()));
    }

    /**
     * GET /api/router/stats
     * Show routing statistics — what % went local vs API.
     *
     * curl http://localhost:8080/api/router/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ModelRouter.RouterStats> stats() {
        return ResponseEntity.ok(modelRouter.getStats());
    }

    /**
     * POST /api/router/classify
     * Classify a query without routing it — useful for debugging.
     *
     * curl -X POST http://localhost:8080/api/router/classify \
     *   -H "Content-Type: application/json" \
     *   -d '{"query": "Explain quantum computing in detail with examples and compare it to classical computing."}'
     */
    @PostMapping("/classify")
    public ResponseEntity<?> classify(@RequestBody QueryRequest req) {
        return ResponseEntity.ok(Map.of(
                "query", req.query(),
                "complexity", QueryClassifier.classify(req.query()).name(),
                "explanation", QueryClassifier.explain(req.query())
        ));
    }

    /**
     * GET /api/router/ollama/health
     * Check if Ollama is running locally.
     *
     * curl http://localhost:8080/api/router/ollama/health
     */
    @GetMapping("/ollama/health")
    public ResponseEntity<?> ollamaHealth() {
        boolean healthy = ollamaService.isHealthy();
        return ResponseEntity.ok(Map.of(
                "ollama_running", healthy,
                "local_url", "http://localhost:11434",
                "message", healthy ? "Local model ready" : "Ollama not running — install from ollama.ai"
        ));
    }

    /**
     * POST /api/router/benchmark
     * Run head-to-head comparison: Ollama vs HuggingFace.
     * Takes 2-5 minutes — makes 10 LLM calls.
     *
     * curl -X POST http://localhost:8080/api/router/benchmark
     */
    @PostMapping("/benchmark")
    public ResponseEntity<ModelBenchmark.BenchmarkReport> benchmark() {
        return ResponseEntity.ok(modelBenchmark.run());
    }
}