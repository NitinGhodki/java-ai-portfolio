package com.aiportfolio.week_a.day7_gateway.ratelimit;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RateLimitInterceptor — intercepts EVERY request to /api/gateway/**
 * before it reaches the controller. Enforces rate limit automatically.
 *
 * Client identification: uses X-Client-Id header, falls back to IP address.
 * In production: use the authenticated user ID or API key instead.
 *
 * This interceptor pattern is Spring's equivalent of the @InputGuardrails
 * structural enforcement you saw in LangChain4j on Day 6 — applied once
 * at the framework level, impossible to accidentally bypass per-endpoint.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig rateLimitConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientId = resolveClientId(request);
        Bucket bucket = rateLimitConfig.resolveBucket(clientId);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true; // allow request through
        }

        // Rate limit exceeded
        long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
        response.setStatus(429); // Too Many Requests
        response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitSeconds));
        log.warn("[RateLimit] BLOCKED client={} — retry after {}s", clientId, waitSeconds);

        try {
            response.getWriter().write(String.format(
                    "{\"error\":\"Rate limit exceeded\",\"retryAfterSeconds\":%d}", waitSeconds
            ));
        } catch (Exception e) {
            log.error("Error writing rate limit response", e);
        }

        return false; // block request
    }

    private String resolveClientId(HttpServletRequest request) {
        String clientHeader = request.getHeader("X-Client-Id");
        if (clientHeader != null && !clientHeader.isBlank()) {
            return clientHeader;
        }
        return request.getRemoteAddr(); // fallback to IP
    }
}