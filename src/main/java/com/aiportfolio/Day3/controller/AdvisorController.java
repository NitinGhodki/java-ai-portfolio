package com.aiportfolio.Day3.controller;

import com.aiportfolio.Day3.advisor.AdvisorPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AdvisorController — REST API for all three pipelines.
 *
 * Endpoints:
 * POST /api/agent/query               — function calling
 * POST /api/agent/rag-memory/query    — RAG + memory
 * POST /api/agent/full/query          — functions + RAG + memory
 * POST /api/agent/stream/query        — streaming with memory
 * DELETE /api/agent/memory/{sessionId} — clear session memory
 * GET  /api/agent/memory/{sessionId}/size — check memory depth
 */

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AdvisorController {

    private final AdvisorPipeline pipeline;

    public record QueryRequest(String question) {}
    public record SessionQueryRequest(String question, String sessionId) {}
    public record QueryResponse(String question, String answer, String pipeline) {}

    /**
     * POST /api/agent/query
     * Function calling only — tools available, no documents, no memory.
     *
     * curl -X POST http://localhost:8080/api/agent/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What is 23% of 15000 rupees?"}'
     */

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> functionQuery(@RequestBody QueryRequest req) {
        String answer = pipeline.queryWithFunctions(req.question());
        return ResponseEntity.ok(new QueryResponse(req.question(), answer, "functions"));
    }

    /**
     * POST /api/agent/rag-memory/query
     * RAG + memory — documents + conversation history.
     *
     * Run these in order to test memory:
     *
     * curl -X POST http://localhost:8080/api/agent/rag-memory/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What does the Professional plan include?", "sessionId": "user123"}'
     *
     * curl -X POST http://localhost:8080/api/agent/rag-memory/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What was the plan I just asked about?", "sessionId": "user123"}'
     *
     * Second call should reference the Professional plan from memory.
     */

    @PostMapping("/rag-memory/query")
    public ResponseEntity<QueryResponse> ragMemoryQuery(@RequestBody SessionQueryRequest req) {
        String answer = pipeline.queryWithRagAndMemory(req.question(), req.sessionId());
        return ResponseEntity.ok(new QueryResponse(req.question(), answer, "rag+memory"));
    }

    /**
     * POST /api/agent/full/query
     * All three: functions + RAG + memory.
     *
     * Test with: "What is the annual cost of the Professional plan with 20% discount?"
     * This requires: RAG (get price from documents) + calculator (compute discount) + memory
     *
     * curl -X POST http://localhost:8080/api/agent/full/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What is the annual cost of Professional plan with 20% discount?", "sessionId": "sess1"}'
     */

    @PostMapping("/full/query")
    public ResponseEntity<QueryResponse> fullQuery(@RequestBody SessionQueryRequest req) {
        String answer = pipeline.queryFull(req.question(), req.sessionId());
        return ResponseEntity.ok(new QueryResponse(req.question(), answer, "full"));
    }

    /**
     * POST /api/agent/stream/query
     * Streaming with memory — tokens arrive one by one.
     *
     * curl -X POST http://localhost:8080/api/agent/stream/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "Explain the refund policy in detail.", "sessionId": "stream1"}' \
     *   --no-buffer
     */
    @PostMapping(value = "/stream/query", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamQuery(@RequestBody SessionQueryRequest req) {
        return pipeline.streamWithMemory(req.question(), req.sessionId());
    }

    /**
     * DELETE /api/agent/memory/{sessionId}
     * Clear a user's conversation history.
     *
     * curl -X DELETE http://localhost:8080/api/agent/memory/user123
     */
    @DeleteMapping("/memory/{sessionId}")
    public ResponseEntity<String> clearMemory(@PathVariable String sessionId) {
        pipeline.clearMemory(sessionId);
        return ResponseEntity.ok("Memory cleared for session: " + sessionId);
    }

    /**
     * GET /api/agent/memory/{sessionId}/size
     *
     * curl http://localhost:8080/api/agent/memory/user123/size
     */
    @GetMapping("/memory/{sessionId}/size")
    public ResponseEntity<Integer> memorySize(@PathVariable String sessionId) {
        return ResponseEntity.ok(pipeline.getMemorySize(sessionId));
    }

}
