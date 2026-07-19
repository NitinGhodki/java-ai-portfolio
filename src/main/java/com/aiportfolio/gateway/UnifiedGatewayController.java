package com.aiportfolio.gateway;

import com.aiportfolio.gateway.dto.QueryRequest;
import com.aiportfolio.gateway.dto.QueryResponse;
import com.aiportfolio.week_a.day6.guardrails.GuardedChatService;
import com.aiportfolio.week_a.day7_gateway.cache.CachedChatService;
import com.aiportfolio.week_b.day10.metrics.AiMetricsService;
import com.aiportfolio.week_b.day11_multiagent.state.WorkflowState;
import com.aiportfolio.week_b.day11_multiagent.workflow.MultiAgentWorkflow;
import com.aiportfolio.week_b.day9.router.ModelRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * UnifiedGatewayController — the portfolio showcase endpoint.
 *
 * POST /api/v1/query — intelligent routing through the full system:
 *
 * 1. Input validation (injection check via guardrail)
 * 2. Query classification (SIMPLE/MODERATE/COMPLEX)
 * 3. Cache check (return immediately if cached)
 * 4. Model routing (local Ollama vs HuggingFace API)
 * 5. RAG retrieval (hybrid search from document store)
 * 6. Response generation
 * 7. Metrics recording
 * 8. Cache store
 * 9. Return response with full metadata
 *
 * This single endpoint demonstrates everything you built in 14 days.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UnifiedGatewayController {

    private final ModelRouter modelRouter;
    private final CachedChatService cachedChatService;
    private final GuardedChatService.GuardedAgent guardedAgent;
    private final MultiAgentWorkflow multiAgentWorkflow;
    private final AiMetricsService metricsService;


    /**
     * POST /api/v1/query
     * The unified intelligent query endpoint.
     *
     * Mode "rag":         cached RAG with model routing
     * Mode "agent":       guardrailed LangChain4j agent
     * Mode "multi-agent": full 3-agent workflow (slow but thorough)
     *
     * curl -X POST https://your-deployed-url/api/v1/query \
     *   -H "Content-Type: application/json" \
     *   -H "X-Client-Id: portfolio-demo" \
     *   -d '{
     *     "question": "What are all pricing plans and their costs?",
     *     "mode": "rag",
     *     "outputFormat": "bullets"
     *   }'
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(
            @RequestBody QueryRequest req,
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId) {

        long start = System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        String mode = req.mode() != null ? req.mode() : "rag";

        log.info("[Gateway] Query: mode={} client={} session={} question={}",
                mode, clientId, sessionId,
                req.question().substring(0, Math.min(50, req.question().length())));

        try {
            return switch (mode) {
                case "rag" -> handleRag(req, clientId, start, sessionId);
                case "agent" -> handleAgent(req, start, sessionId);
                case "multi-agent" -> handleMultiAgent(req, start, sessionId);
                default -> handleRag(req, clientId, start, sessionId);
            };
        } catch (Exception e) {
            log.error("[Gateway] Query failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new QueryResponse(
                            req.question(), "Error: " + e.getMessage(),
                            mode, "none", false, false,
                            System.currentTimeMillis() - start,
                            Map.of("error", e.getClass().getSimpleName())
                    )
            );
        }
    }

    private ResponseEntity<QueryResponse> handleRag(
            QueryRequest req, String clientId, long start, String sessionId) {

        // Check cache first
        long cacheCheckStart = System.currentTimeMillis();
        var ragResponse = cachedChatService.queryCached(req.question(), 3);
        boolean cached = (System.currentTimeMillis() - cacheCheckStart) < 50;

        if (cached) {
            metricsService.recordCacheHit();
        } else {
            metricsService.recordCacheMiss();
        }

        long latencyMs = System.currentTimeMillis() - start;
        metricsService.recordLlmCall("rag", latencyMs,
                (req.question() + ragResponse.answer()).split("\\s+").length);

        return ResponseEntity.ok(new QueryResponse(
                req.question(),
                ragResponse.answer(),
                "rag",
                ragResponse.mode(),
                cached,
                false,
                latencyMs,
                Map.of(
                        "sources", ragResponse.sources(),
                        "sessionId", sessionId,
                        "clientId", clientId
                )
        ));
    }

    private ResponseEntity<QueryResponse> handleAgent(
            QueryRequest req, long start, String sessionId) {

        String answer;
        boolean blocked = false;

        try {
            answer = guardedAgent.chat(req.question());
            metricsService.recordLlmCall("agent", System.currentTimeMillis() - start,
                    (req.question() + answer).split("\\s+").length);
        } catch (Exception e) {
            answer = "BLOCKED: " + e.getMessage();
            blocked = true;
            metricsService.recordGuardrailBlock("injection_or_pii");
        }

        return ResponseEntity.ok(new QueryResponse(
                req.question(), answer, "agent",
                "langchain4j-guarded-agent", false, blocked,
                System.currentTimeMillis() - start,
                Map.of("sessionId", sessionId, "guardrailsActive", true)
        ));
    }

    private ResponseEntity<QueryResponse> handleMultiAgent(
            QueryRequest req, long start, String sessionId) {

        var state = WorkflowState.initial(
                req.question(),
                req.outputFormat() != null ? req.outputFormat() : "paragraph",
                sessionId
        );

        var result = multiAgentWorkflow.runSequential(state);

        return ResponseEntity.ok(new QueryResponse(
                req.question(),
                result.finalOutput(),
                "multi-agent",
                "researcher+writer+critic",
                false, false,
                System.currentTimeMillis() - start,
                Map.of(
                        "executionLog", result.executionLog(),
                        "sessionId", sessionId,
                        "agents", "Researcher → Writer → Critic → Supervisor"
                )
        ));
    }

    /**
     * GET /api/v1/status
     * Public status page — shows what the gateway can do.
     * This is what you link to from your LinkedIn post.
     *
     * curl https://your-deployed-url/api/v1/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        var snapshot = metricsService.getSnapshot();
        return ResponseEntity.ok(Map.of(
                "name", "Enterprise AI Gateway",
                "version", "1.0.0",
                "status", "operational",
                "capabilities", Map.of(
                        "rag", "Hybrid RAG with Spring AI + ChromaDB",
                        "agent", "Guardrailed LangChain4j agent",
                        "multi_agent", "3-agent workflow (Researcher + Writer + Critic)",
                        "local_model", "Ollama Mistral (if available)",
                        "rate_limiting", "10 req/min per client (Bucket4j)",
                        "caching", "1-hour response cache (Caffeine)"
                ),
                "metrics", Map.of(
                        "totalQueries", snapshot.totalLlmCalls(),
                        "cacheHitRate", String.format("%.1f%%", snapshot.cacheHitRate() * 100),
                        "avgLatencyMs", Math.round(snapshot.avgLlmLatencyMs()),
                        "guardrailBlocks", snapshot.guardrailBlockCount()
                ),
                "frameworks", Map.of(
                        "spring_ai", "1.0.0",
                        "langchain4j", "0.36.2",
                        "java", "21",
                        "spring_boot", "3.3.x"
                )
        ));
    }
}
