package com.aiportfolio.week_a.day6.rag.advanced;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SimpleReranker — custom ContentAggregator implementing reranking logic.
 *
 * Python Week 2 Day 12 used a cross-encoder model for reranking.
 * True cross-encoders aren't readily available as Java libraries yet —
 * this is a documented gap in the Java AI ecosystem (mention this in interviews).
 *
 * This implementation uses a practical alternative: re-score candidates
 * using TWO embedding similarity passes — original query embedding AND
 * a keyword-overlap boost. Not as accurate as a true cross-encoder,
 * but demonstrates the reranking PATTERN: retrieve broad, then narrow by
 * a more precise scoring method.
 *
 * For production: call Cohere's rerank API (LangChain4j has built-in
 * support for it) — that gives you a real cross-encoder without
 * needing a local Java cross-encoder library.
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleReranker implements ContentAggregator {

    private final EmbeddingModel embeddingModel;
    private static final int FINAL_TOP_K = 3;

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        // Flatten all retrieved content from all query variations into one list
        List<Content> allContent = queryToContents.values().stream()
                .flatMap(Collection::stream)
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());

        log.info("[Rerank] {} candidates before reranking", allContent.size());

        if (allContent.isEmpty()) return allContent;

        // Use the first query's text for rerank scoring
        String queryText = queryToContents.keySet().iterator().next().text();
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();

        // Score each candidate: combine embedding similarity + keyword overlap
        List<ScoredContent> scored = allContent.stream()
                .map(content -> {
                    TextSegment segment = content.textSegment();
                    Embedding contentEmbedding = embeddingModel.embed(segment.text()).content();

                    double cosineSim = cosineSimilarity(
                            queryEmbedding.vector(), contentEmbedding.vector()
                    );
                    double keywordBoost = keywordOverlapScore(queryText, segment.text());

                    // Combined score — weighted toward semantic similarity
                    double finalScore = (0.7 * cosineSim) + (0.3 * keywordBoost);

                    return new ScoredContent(content, finalScore);
                })
                .sorted(Comparator.comparingDouble(ScoredContent::score).reversed())
                .limit(FINAL_TOP_K)
                .collect(Collectors.toList());

        log.info("[Rerank] Top {} after reranking, scores: {}",
                scored.size(),
                scored.stream().map(s -> String.format("%.3f", s.score())).collect(Collectors.toList()));

        return scored.stream().map(ScoredContent::content).collect(Collectors.toList());
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double keywordOverlapScore(String query, String content) {
        Set<String> queryWords = new HashSet<>(Arrays.asList(query.toLowerCase().split("\\s+")));
        Set<String> contentWords = new HashSet<>(Arrays.asList(content.toLowerCase().split("\\s+")));
        queryWords.retainAll(contentWords);
        return queryWords.isEmpty() ? 0.0 : (double) queryWords.size() / Math.max(1, query.split("\\s+").length);
    }

    private record ScoredContent(Content content, double score) {}
}
