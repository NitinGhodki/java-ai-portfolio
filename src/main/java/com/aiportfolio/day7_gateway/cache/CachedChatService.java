package com.aiportfolio.day7_gateway.cache;

import com.aiportfolio.Day2.rag.RagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * CachedChatService — wraps RagPipeline (from Day 2) with caching.
 *
 * @Cacheable("ragResponses") — Spring intercepts every call to query().
 * On first call with a given normalizedQuestion: executes the method,
 * stores the result in the cache keyed by the question.
 * On subsequent calls with the SAME normalized question: returns
 * the cached result WITHOUT calling query() again — zero LLM cost.
 *
 * Normalization matters: "What is the refund policy?" and
 * "what is the refund policy" (different case, no punctuation)
 * should hit the same cache entry. Without normalization,
 * cache hit rate drops significantly.
 *
 * This is the single highest-ROI optimization in this entire gateway —
 * for a FAQ-style endpoint, cache hit rates of 60-80% are realistic,
 * directly cutting your LLM API costs by the same percentage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CachedChatService {

    private final RagPipeline ragPipeline;

    @Cacheable(value = "ragResponses", key = "#root.target.normalize(#question)")
    public RagPipeline.RagResponse queryCached(String question, int topK) {
        log.info("[Cache MISS] Calling LLM for: {}", question);
        return ragPipeline.queryWithAdvisor(question, topK);
    }

    /**
     * Normalizes a question for use as a cache key.
     * Must be public for SpEL expression #root.target.normalize() to access it.
     */
    public String normalize(String question) {
        return question.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", "");
    }
}