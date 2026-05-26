package com.aiportfolio.day1.controller;

import com.aiportfolio.day1.llm.LLMClient;
import com.aiportfolio.day1.llm.PromptPatterns;
import com.aiportfolio.day1.llm.TokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * ChatController — REST API for Day 1 LLM features.
 *
 * Endpoints:
 * POST /api/chat          — single call, returns full response
 * POST /api/chat/stream   — streaming via SSE, returns tokens as they arrive
 * POST /api/chat/system   — chat with custom system prompt
 * GET  /api/patterns      — run all 3 prompt patterns, return comparison
 * POST /api/tokens/count  — count tokens and estimate cost
 *
 * This is what you show in your README with curl screenshots.
 */

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final LLMClient llmClient;
    private final PromptPatterns promptPatterns;

    // Request / Response DTOs

    public record ChatRequest(String message) {}
    public record ChatWithSystemRequest(String systemPrompt, String message) {}
    public record TokenCountRequest(String text, String model) {}

    public record ChatResponse(
            String message,
            String response,
            int approxTokens,
            double estimatedCostUsd,
            long latencyMs
    ) {}

    public record TokenCountResponse(
            String text,
            int approximateTokens,
            TokenUtils.CostComparison costComparison
    ) {}

    // Endpoint 1: Basic chat
    /**
     * POST /api/chat
     * Body: {"message": "What is machine learning?"}
     *
     * curl -X POST http://localhost:8080/api/chat \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "What is machine learning in one sentence?"}'
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.message().substring(0, Math.min(50, request.message().length())));

        long start = System.currentTimeMillis();
        String response = llmClient.chat(request.message());
        long latencyMs = System.currentTimeMillis() - start;

        int tokens = TokenUtils.approximateTokens(response);
        double cost = TokenUtils.estimateCost(
                TokenUtils.approximateTokens(request.message()),
                tokens,
                "Qwen/Qwen2.5-7B-Instruct"
        );

        return ResponseEntity.ok(new ChatResponse(
                request.message(),
                response,
                tokens,
                cost,
                latencyMs
        ));
    }

    // Endpoint 2: Streaming chat via SSE

    /**
     * POST /api/chat/stream
     * Returns Server-Sent Events — tokens arrive one by one.
     *
     * This is the answer to your Python Day 1 Q3:
     * "What protocol sends streaming LLM chunks to a browser?"
     * Answer: Server-Sent Events (text/event-stream)
     *
     * MediaType.TEXT_EVENT_STREAM_VALUE = "text/event-stream"
     * The browser or curl keeps the connection open and reads chunks.
     *
     * curl -X POST http://localhost:8080/api/chat/stream \
     *   -H "Content-Type: application/json" \
     *   -d '{"message": "Count from 1 to 5, one per line."}' \
     *   --no-buffer
     */
    @PostMapping(
            value = "/chat/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Streaming chat request: {}", request.message());

        return llmClient.stream(request.message())
                .doOnNext(chunk -> log.trace("Chunk: {}", chunk))
                .doOnComplete(() -> log.info("Stream complete"))
                .doOnError(e -> log.error("Stream error: {}", e.getMessage()));
    }

    // Endpoint 3: Chat with custom system prompt

    /**
     * POST /api/chat/system
     * Body: {"systemPrompt": "You are a pirate.", "message": "What is Java?"}
     *
     * curl -X POST http://localhost:8080/api/chat/system \
     *   -H "Content-Type: application/json" \
     *   -d '{"systemPrompt": "You are a pirate. Answer like a pirate.", "message": "What is Java?"}'
     */
    @PostMapping("/chat/system")
    public ResponseEntity<ChatResponse> chatWithSystem(@RequestBody ChatWithSystemRequest request) {
        log.info("Chat with system prompt. System: {}", request.systemPrompt());

        long start = System.currentTimeMillis();
        String response = llmClient
                .withSystemPrompt(request.systemPrompt())
                .chat(request.message());
        long latencyMs = System.currentTimeMillis() - start;

        return ResponseEntity.ok(new ChatResponse(
                request.message(),
                response,
                TokenUtils.approximateTokens(response),
                TokenUtils.estimateCost(
                        TokenUtils.approximateTokens(request.systemPrompt() + request.message()),
                        TokenUtils.approximateTokens(response),
                        "Qwen/Qwen2.5-7B-Instruct"
                ),
                latencyMs
        ));
    }

    // Endpoint 4: Prompt pattern comparison

    /**
     * GET /api/patterns
     * Runs zero-shot, few-shot, and chain-of-thought on the same question.
     * Returns all three responses with token counts and latency.
     *
     * curl http://localhost:8080/api/patterns
     *
     * This call takes ~30-60 seconds — it makes 3 LLM calls sequentially.
     * In production you would run these in parallel with CompletableFuture.
     */
    @GetMapping("/patterns")
    public ResponseEntity<PromptPatterns.PatternComparisonResult> comparePatterns() {
        log.info("Running prompt pattern comparison...");
        var result = promptPatterns.compareAll();
        log.info("Patterns complete. Tokens: zs={}, fs={}, cot={}",
                result.getZeroShotApproxTokens(),
                result.getFewShotApproxTokens(),
                result.getChainOfThoughtApproxTokens());
        return ResponseEntity.ok(result);
    }

    // Endpoint 5: Token count and cost estimate

    /**
     * POST /api/tokens/count
     * Body: {"text": "your text here", "model": "Qwen/Qwen2.5-7B-Instruct"}
     *
     * curl -X POST http://localhost:8080/api/tokens/count \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "What is machine learning?", "model": "Qwen/Qwen2.5-7B-Instruct"}'
     */
    @PostMapping("/tokens/count")
    public ResponseEntity<TokenCountResponse> countTokens(@RequestBody TokenCountRequest request) {
        int tokens = TokenUtils.approximateTokens(request.text());
        var comparison = TokenUtils.compareCosts(tokens, tokens); // assume equal input/output

        return ResponseEntity.ok(new TokenCountResponse(
                request.text(),
                tokens,
                comparison
        ));
    }

}
