package com.aiportfolio.week_b.day9.benchmark;

import com.aiportfolio.week_b.day9.ollama.OllamaService;
import com.aiportfolio.week_b.day9.router.QueryClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ModelBenchmark — compare HuggingFace vs Ollama head-to-head.
 *
 * Runs the same 5 queries through both models and measures:
 *   - Latency (ms) per query
 *   - Response quality (subjective — you read the outputs)
 *   - Cost (Ollama = $0, HuggingFace = calculated)
 *
 * This benchmark is your Day 13 blog post data.
 * "I measured Java AI performance: local vs API" with real numbers
 * is more credible than general claims.
 *
 * Run this once, save the output, put numbers in README.
 * These numbers become interview talking points.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelBenchmark {

    private final ChatModel huggingFaceChatModel;
    private final OllamaService ollamaService;

    private static final List<String> BENCHMARK_QUERIES = List.of(
            "What is the capital of India?",
            "Explain what a REST API is in two sentences.",
            "What is 15% of 8500?",
            "List three benefits of using Java for enterprise applications.",
            "Explain the difference between SQL and NoSQL databases."
    );

    public BenchmarkReport run() {
        log.info("[Benchmark] Starting head-to-head comparison");
        List<QueryBenchmark> results = new ArrayList<>();

        for (String query : BENCHMARK_QUERIES) {
            log.info("[Benchmark] Testing: {}...", query.substring(0, Math.min(40, query.length())));

            // HuggingFace
            long hfStart = Instant.now().toEpochMilli();
            String hfResponse;
            try {
                hfResponse = ChatClient.create(huggingFaceChatModel)
                        .prompt(query)
                        .call()
                        .content();
            } catch (Exception e) {
                hfResponse = "ERROR: " + e.getMessage();
            }
            long hfLatency = Instant.now().toEpochMilli() - hfStart;

            // Ollama
            long ollamaStart = Instant.now().toEpochMilli();
            String ollamaResponse;
            try {
                ollamaResponse = ollamaService.chat(query);
            } catch (Exception e) {
                ollamaResponse = "ERROR: " + e.getMessage();
            }
            long ollamaLatency = Instant.now().toEpochMilli() - ollamaStart;

            // Cost calculation
            int hfTokens = (query + hfResponse).split("\\s+").length;
            double hfCost = (hfTokens / 1000.0) * 0.0001;

            results.add(new QueryBenchmark(
                    query,
                    QueryClassifier.classify(query).name(),
                    hfResponse,
                    hfLatency,
                    hfCost,
                    ollamaResponse,
                    ollamaLatency,
                    0.0 // Ollama is always free
            ));
        }

        // Aggregate
        double avgHfLatency = results.stream().mapToLong(r -> r.hfLatencyMs()).average().orElse(0);
        double avgOllamaLatency = results.stream().mapToLong(r -> r.ollamaLatencyMs()).average().orElse(0);
        double totalHfCost = results.stream().mapToDouble(r -> r.hfCostUsd()).sum();

        return new BenchmarkReport(
                results,
                avgHfLatency,
                avgOllamaLatency,
                totalHfCost,
                0.0,
                String.format("Ollama is %.1fx faster than HuggingFace API on these queries.",
                        avgHfLatency / Math.max(1, avgOllamaLatency))
        );
    }

    public record QueryBenchmark(
            String query,
            String complexity,
            String hfResponse,
            long hfLatencyMs,
            double hfCostUsd,
            String ollamaResponse,
            long ollamaLatencyMs,
            double ollamaCostUsd
    ) {}

    public record BenchmarkReport(
            List<QueryBenchmark> queryResults,
            double avgHfLatencyMs,
            double avgOllamaLatencyMs,
            double totalHfCostUsd,
            double totalOllamaCostUsd,
            String summary
    ) {}
}