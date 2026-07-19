package com.aiportfolio.gateway.dto;

import java.util.Map;

public record QueryResponse(
        String question,
        String answer,
        String mode,
        String modelUsed,
        boolean cached,
        boolean blocked,
        long latencyMs,
        Map<String, Object> metadata
) {}