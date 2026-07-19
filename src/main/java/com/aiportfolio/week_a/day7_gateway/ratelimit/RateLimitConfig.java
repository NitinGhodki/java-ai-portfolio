package com.aiportfolio.week_a.day7_gateway.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * RateLimitConfig — token bucket rate limiting per client.
 *
 * Each client (identified by API key or IP) gets their own Bucket.
 * Bucket holds tokens. Each request consumes 1 token.
 * Tokens refill at a fixed rate.
 *
 * Configuration: 10 requests per minute per client.
 * Tune based on your actual API cost budget.
 *
 * ConcurrentHashMap stores one bucket per client — same pattern
 * as your session memory map from Day 3, same memory-leak risk.
 * Production fix: same as Day 3 — use Caffeine cache with TTL
 * instead of unbounded ConcurrentHashMap (left as exercise —
 * you already know this fix from Day 3 Q2).
 */
@Component
public class RateLimitConfig {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int REQUESTS_PER_MINUTE = 10;

    public Bucket resolveBucket(String clientId) {
        return buckets.computeIfAbsent(clientId, this::newBucket);
    }

    private Bucket newBucket(String clientId) {
        Bandwidth limit = Bandwidth.classic(
                REQUESTS_PER_MINUTE,
                Refill.greedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    public int activeClientCount() {
        return buckets.size();
    }
}