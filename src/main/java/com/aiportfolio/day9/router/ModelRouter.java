package com.aiportfolio.day9.router;

import com.aiportfolio.day9.ollama.OllamaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ModelRouter — routes queries to the appropriate model.
 *
 * Routing logic:
 *   SIMPLE   → Ollama (local, free, private, fast)
 *   MODERATE → HuggingFace Mistral-7B (balanced)
 *   COMPLEX  → HuggingFace Mixtral-8x7B or best available (capable)
 *
 * Health-aware routing:
 *   If Ollama is down (not running), simple queries fall back to HuggingFace.
 *   The system degrades gracefully — never fails completely.
 *   This is production-grade robustness: always have a fallback.
 *
 * Cost tracking:
 *   Every routing decision is logged with model used and latency.
 *   Over time this log shows: what % of queries go local (free) vs API (cost).
 *   Use this data to tune the classifier thresholds.
 *
 * Python equivalent: your supervisor_route() from Week 2 Day 13,
 * but routing between models instead of between agents.
 */
@Slf4j
@Service
public class ModelRouter {

    private final ChatModel huggingFaceChatModel;
    private final OllamaService ollamaService;

    public ModelRouter(
            @Qualifier("huggingFaceChatModel") ChatModel huggingFaceChatModel,
            OllamaService ollamaService) {
        this.huggingFaceChatModel = huggingFaceChatModel;
        this.ollamaService = ollamaService;
    }

    // Tracks routing statistics for the /stats endpoint
    private long simpleCount = 0;
    private long moderateCount = 0;
    private long complexCount = 0;
    private long ollamaFallbackCount = 0;

    /**
     * Route a query to the appropriate model and return the response.
     * Records latency and routing decision for each call.
     */
    public RouterResponse route(String query) {
        long start = Instant.now().toEpochMilli();
        var complexity = QueryClassifier.classify(query);

        String modelUsed;
        String response;
        boolean usedLocal;

        switch (complexity) {
            case SIMPLE -> {
                simpleCount++;
                if (ollamaService.isHealthy()) {
                    log.info("[Router] SIMPLE → Ollama (local, free)");
                    response = ollamaService.chat(query);
                    modelUsed = "ollama/mistral (local)";
                    usedLocal = true;
                } else {
                    log.warn("[Router] Ollama unavailable, falling back to HuggingFace");
                    ollamaFallbackCount++;
                    response = callHuggingFace(query);
                    modelUsed = "google/gemma-4-26B-A4B-it (fallback)";
                    usedLocal = false;
                }
            }
            case MODERATE -> {
                moderateCount++;
                log.info("[Router] MODERATE → HuggingFace Mistral-7B");
                response = callHuggingFace(query);
                modelUsed = "google/gemma-4-26B-A4B-it";
                usedLocal = false;
            }
            case COMPLEX -> {
                complexCount++;
                log.info("[Router] COMPLEX → HuggingFace (best model)");
                // In production: swap to Mixtral-8x7B or GPT-4o here
                response = callHuggingFaceWithSystem(query,
                        "You are a highly capable AI assistant. Think carefully and provide comprehensive, accurate answers.");
                modelUsed = "google/gemma-4-26B-A4B-it (complex)";
                usedLocal = false;
            }
            default -> {
                response = callHuggingFace(query);
                modelUsed = "google/gemma-4-26B-A4B-it";
                usedLocal = false;
            }
        }

        long latencyMs = Instant.now().toEpochMilli() - start;
        double estimatedCost = usedLocal ? 0.0 : estimateCost(query + response);

        log.info("[Router] Complete: model={} latency={}ms cost=${} complexity={}",
                modelUsed, latencyMs, estimatedCost, complexity);

        return new RouterResponse(
                query,
                response,
                complexity.name(),
                modelUsed,
                latencyMs,
                estimatedCost,
                usedLocal,
                QueryClassifier.explain(query)
        );
    }

    private String callHuggingFace(String query) {
        return ChatClient.create(huggingFaceChatModel)
                .prompt(query)
                .call()
                .content();
    }

    private String callHuggingFaceWithSystem(String query, String systemPrompt) {

        String response = ChatClient.create(huggingFaceChatModel)
                .prompt()
                .system(systemPrompt)
                .user(query)
                .call()
                .content();

        return (response != null) ? response.trim() : "No response generated.";

    }

    private double estimateCost(String text) {
        int tokens = text.split("\\s+").length;
        return Math.round((tokens / 1000.0) * 0.0001 * 1_000_000.0) / 1_000_000.0;
    }

    public RouterStats getStats() {
        long total = simpleCount + moderateCount + complexCount;
        return new RouterStats(
                total,
                simpleCount,
                moderateCount,
                complexCount,
                ollamaFallbackCount,
                total > 0 ? (double) simpleCount / total : 0.0,
                total > 0 ? (double) complexCount / total : 0.0
        );
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record RouterResponse(
            String query,
            String answer,
            String complexity,
            String modelUsed,
            long latencyMs,
            double estimatedCostUsd,
            boolean usedLocalModel,
            String classificationExplanation
    ) {}

    public record RouterStats(
            long totalQueries,
            long simpleCount,
            long moderateCount,
            long complexCount,
            long ollamaFallbackCount,
            double localModelPercentage,
            double complexPercentage
    ) {}
}