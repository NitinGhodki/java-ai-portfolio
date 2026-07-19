package com.aiportfolio.week_a.day7_gateway.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * CacheConfig — Caffeine-backed response caching for LLM calls.
 *
 * @EnableCaching activates Spring's caching annotations (@Cacheable)
 * application-wide. CaffeineCacheManager provides the actual storage.
 *
 * expireAfterWrite(1 hour): cached answers go stale after 1 hour.
 * Tune based on how often your underlying documents change.
 *
 * maximumSize(1000): caps memory usage — same bounded-cache lesson
 * from Day 3's session memory leak, applied correctly from day one here.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("ragResponses");
        cacheManager.setCaffeine(
                Caffeine.newBuilder()
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .maximumSize(1000)
                        .recordStats() // enables cache hit/miss metrics
        );
        return cacheManager;
    }
}
