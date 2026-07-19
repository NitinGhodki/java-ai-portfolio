package com.aiportfolio.week_a.day7_gateway.gateway;

import com.aiportfolio.week_a.day6.guardrails.GuardedChatService;
import com.aiportfolio.week_a.day7_gateway.cache.CachedChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * AiGatewayController — the unified production endpoint.
 *
 * This is the Day 7 deliverable: ONE gateway combining
 * Spring AI RAG (cached) + LangChain4j guardrailed agent,
 * protected by rate limiting (via interceptor, applies to /api/gateway/**).
 *
 * 6 endpoints total — your portfolio API surface.
 */
@Slf4j
@RestController
@RequestMapping("/api/gateway")
@RequiredArgsConstructor
public class AiGatewayController {

    private final CachedChatService cachedChatService;
    private final GuardedChatService.GuardedAgent guardedAgent;

    public record RagRequest(String question, Integer topK) {}
    public record ChatRequest(String message) {}

    /**
     * POST /api/gateway/rag — cached, rate-limited RAG endpoint.
     *
     * Run this TWICE with the same question to see cache behaviour:
     * curl -X POST http://localhost:8080/api/gateway/rag \
     *   -H "Content-Type: application/json" \
     *   -H "X-Client-Id: demo-user" \
     *   -d '{"question": "What is the refund policy?", "topK": 3}'
     *
     * First call: slow (LLM call). Second call: instant (cache hit).
     */
    @PostMapping("/rag")
    public ResponseEntity<?> ragQuery(
            @RequestBody RagRequest req,
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId) {

        long start = Instant.now().toEpochMilli();
        int topK = req.topK() != null ? req.topK() : 3;
        var response = cachedChatService.queryCached(req.question(), topK);
        long latency = Instant.now().toEpochMilli() - start;

        return ResponseEntity.ok(Map.of(
                "question", req.question(),
                "answer", response.answer(),
                "sources", response.sources(),
                "latencyMs", latency,
                "cacheLikelyHit", latency < 50 // heuristic — cached calls return near-instantly
        ));
    }

    /**
     * POST /api/gateway/chat — guardrailed agent endpoint.
     *
     * curl -X POST http://localhost:8080/api/gateway/chat \
     *   -H "Content-Type: application/json" \
     *   -H "X-Client-Id: demo-user" \
     *   -d '{"message": "What is your refund policy?"}'
     */
    @PostMapping("/chat")
    public ResponseEntity<?> guardedChat(
            @RequestBody ChatRequest req,
            @RequestHeader(value = "X-Client-Id", defaultValue = "anonymous") String clientId) {
        try {
            String response = guardedAgent.chat(req.message());
            return ResponseEntity.ok(Map.of("response", response, "blocked", false));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("response", "BLOCKED: " + e.getMessage(), "blocked", true));
        }
    }

    /**
     * GET /api/gateway/health — gateway-specific health check.
     *
     * curl http://localhost:8080/api/gateway/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "rateLimit", "10 req/min per client",
                "cache", "1 hour TTL, 1000 max entries"
        ));
    }

    /**
     * Test rate limiting — call this 12 times rapidly with the same X-Client-Id.
     * Requests 1-10 succeed. Requests 11-12 return 429.
     *
     * for i in {1..12}; do
     *   curl -s -o /dev/null -w "%{http_code}\n" \
     *     -X POST http://localhost:8080/api/gateway/rag \
     *     -H "Content-Type: application/json" \
     *     -H "X-Client-Id: rate-test-user" \
     *     -d '{"question": "test question '"$i"'", "topK": 1}'
     * done
     */
}
