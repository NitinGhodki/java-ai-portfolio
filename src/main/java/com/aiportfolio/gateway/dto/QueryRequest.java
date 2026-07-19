package com.aiportfolio.gateway.dto;

public record QueryRequest(
        String question,
        String mode,         // "rag", "agent", "multi-agent"
        String outputFormat  // "paragraph", "bullets", "table"
) {}