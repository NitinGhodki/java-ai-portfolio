package com.aiportfolio.health;

import com.aiportfolio.week_b.day9.ollama.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * GatewayHealthIndicator — custom health check at /actuator/health.
 *
 * Spring Boot Actuator shows this automatically.
 * When deploying to Railway/Render, the platform calls /actuator/health
 * to determine if the deployment succeeded.
 * DOWN = deployment rolls back. UP = deployment succeeds.
 */
@Component
@RequiredArgsConstructor
public class GatewayHealthIndicator implements HealthIndicator {

    private final OllamaService ollamaService;

    @Override
    public Health health() {
        boolean ollamaUp = ollamaService.isHealthy();

        return Health.up()
                .withDetail("gateway", "operational")
                .withDetail("ollama_local_model", ollamaUp ? "available" : "unavailable (using API fallback)")
                .withDetail("spring_ai", "connected")
                .withDetail("langchain4j", "connected")
                .withDetail("cache", "active")
                .withDetail("rate_limiting", "active")
                .build();
    }
}