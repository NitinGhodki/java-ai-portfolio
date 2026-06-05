package com.aiportfolio.day4_langchain4j.controller;

import com.aiportfolio.day4_langchain4j.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * LangChain4jController — REST API for all LangChain4j services.
 *
 * Compare each endpoint to Spring AI Day 1-3 equivalents.
 * Same behaviour. Different internal implementation.
 * Understanding both makes you dangerous in interviews.
 */
@Slf4j
@RestController
@RequestMapping("/api/lc4j")
@RequiredArgsConstructor
public class LangChain4jController {

    private final BasicChatService basicChatService;
    private final AgentService agentService;
    private final ConversationalService conversationalService;
    private final FullAgentService fullAgentService;
    private final RagService ragService;

    public record ChatRequest(String message) {}
    public record SessionRequest(String sessionId, String message) {}
    public record IngestRequest(String text, String docName) {}

    // ── Endpoint 1: Basic chat ─────────────────────────────────────────────

    /**
     * curl -X POST http://localhost:8081/api/lc4j/chat \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "What is LangChain4j?"}'
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req) {
        long start = Instant.now().toEpochMilli();
        String response = basicChatService.chat(req.message());
        return ResponseEntity.ok(Map.of(
                "message", req.message(),
                "response", response,
                "latencyMs", Instant.now().toEpochMilli() - start
        ));
    }

    // ── Endpoint 2: Agent with tools ──────────────────────────────────────

    /**
     * Test with math: "What is 23% of 15000?"
     * Test with date: "What year is it and how many years since 2000?"
     * Test with both: "Convert 2999 rupees to USD"
     *
     * curl -X POST http://localhost:8081/api/lc4j/agent/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "What is 23% of 15000 and convert that to USD?"}'
     */
    @PostMapping("/agent/query")
    public ResponseEntity<?> agentQuery(@RequestBody ChatRequest req) {
        long start = Instant.now().toEpochMilli();
        String response = agentService.query(req.message());
        return ResponseEntity.ok(Map.of(
                "question", req.message(),
                "answer", response,
                "latencyMs", Instant.now().toEpochMilli() - start
        ));
    }

    // ── Endpoint 3: Conversation with memory ─────────────────────────────

    /**
     * Run turn 1 then turn 2 to see memory working.
     *
     * Turn 1:
     * curl -X POST http://localhost:8081/api/lc4j/conversation \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId": "nitin-001", "message": "My name is Nitin and I am learning Spring AI."}'
     *
     * Turn 2:
     * curl -X POST http://localhost:8081/api/lc4j/conversation \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId": "nitin-001", "message": "What did I tell you about myself?"}'
     *
     * Expected: second response mentions your name and Spring AI.
     */
    @PostMapping("/conversation")
    public ResponseEntity<?> conversation(@RequestBody SessionRequest req) {
        String response = conversationalService.chat(req.sessionId(), req.message());
        return ResponseEntity.ok(Map.of(
                "sessionId", req.sessionId(),
                "message", req.message(),
                "response", response
        ));
    }

    // ── Endpoint 4: Full agent — tools + memory ───────────────────────────

    /**
     * Multi-step agent with memory.
     *
     * Turn 1: establish context
     * curl -X POST http://localhost:8081/api/lc4j/agent/full \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId": "s1", "message": "I am evaluating the Professional plan at 2999 rupees."}'
     *
     * Turn 2: use tools + reference memory
     * curl -X POST http://localhost:8081/api/lc4j/agent/full \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId": "s1", "message": "What plan was I looking at, and what is its annual cost with 20% discount?"}'
     */
    @PostMapping("/agent/full")
    public ResponseEntity<?> fullAgent(@RequestBody SessionRequest req) {
        long start = Instant.now().toEpochMilli();
        String response = fullAgentService.query(req.sessionId(), req.message());
        return ResponseEntity.ok(Map.of(
                "sessionId", req.sessionId(),
                "question", req.message(),
                "answer", response,
                "latencyMs", Instant.now().toEpochMilli() - start
        ));
    }

    // ── Endpoint 5: RAG ingest ────────────────────────────────────────────

    /**
     * curl -X POST http://localhost:8081/api/lc4j/rag/ingest \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "The Starter plan costs 999 rupees per month. The Professional plan costs 2999 rupees per month.", "docName": "pricing.txt"}'
     */
    @PostMapping("/rag/ingest")
    public ResponseEntity<?> ragIngest(@RequestBody IngestRequest req) {
        int segments = ragService.ingest(req.text(), req.docName());
        return ResponseEntity.ok(Map.of(
                "docName", req.docName(),
                "segmentsCreated", segments,
                "status", "ingested"
        ));
    }

    // ── Endpoint 6: RAG query ─────────────────────────────────────────────

    /**
     * curl -X POST http://localhost:8081/api/lc4j/rag/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"sessionId": "rag-session-1", "message": "What does the Professional plan include?"}'
     */
    @PostMapping("/rag/query")
    public ResponseEntity<?> ragQuery(@RequestBody SessionRequest req) {
        long start = Instant.now().toEpochMilli();
        String answer = ragService.query(req.message(), req.sessionId());
        return ResponseEntity.ok(Map.of(
                "sessionId", req.sessionId(),
                "question", req.message(),
                "answer", answer,
                "latencyMs", Instant.now().toEpochMilli() - start
        ));
    }
}