package com.aiportfolio.week_a.day1.llm;

import java.util.Map;

/**
 * TokenUtils — Java equivalent of your Python Day 1 utils/token_utils.py
 *
 * No exact tokenizer available in Java without loading the full model.
 * Use the 0.75 words-per-token approximation — same as your Python version.
 * Accurate to within ~10% for English text.
 */

public class TokenUtils {

    // Approximate cost per 1K tokens (USD) — update when pricing changes
    private static final Map<String, double[]> COST_PER_1K = Map.of(
            // {input_cost, output_cost}
            "mistralai/Mistral-7B-Instruct-v0.3", new double[]{0.0001, 0.0001},
            "gpt-4o",                         new double[]{0.005,  0.015},
            "gpt-4o-mini",                    new double[]{0.00015, 0.0006},
            "Qwen/Qwen2.5-7B-Instruct",       new double[]{0.00004, 0.00007}
    );

    private TokenUtils() {}

    /**
     * Approximate token count using 0.75 words-per-token heuristic.
     * Same formula as your Python version.
     */
    public static int approximateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int wordCount = text.split("\\s+").length;
        return Math.max(1, (int) (wordCount / 0.75));
    }

    /**
     * Estimate cost in USD given token counts and model name.
     */
    public static double estimateCost(int promptTokens, int completionTokens, String model) {
        double[] pricing = COST_PER_1K.getOrDefault(
                model,
                new double[]{0.0001, 0.0001}  // default fallback
        );
        double inputCost  = (promptTokens    / 1000.0) * pricing[0];
        double outputCost = (completionTokens / 1000.0) * pricing[1];
        return Math.round((inputCost + outputCost) * 1_000_000.0) / 1_000_000.0;
    }

    /**
     * Compare costs across providers for the same token counts.
     * Shows what the same call would cost on different models.
     */
    public static CostComparison compareCosts(int promptTokens, int completionTokens) {
        return CostComparison.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .costHuggingFaceUsd(estimateCost(promptTokens, completionTokens,
                        "Qwen/Qwen2.5-7B-Instruct"))
                .costGpt4oUsd(estimateCost(promptTokens, completionTokens, "gpt-4o"))
                .costGpt4oMiniUsd(estimateCost(promptTokens, completionTokens, "gpt-4o-mini"))
                .build();
    }


    @lombok.Builder
    @lombok.Data
    public static class CostComparison {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private double costHuggingFaceUsd;
        private double costGpt4oUsd;
        private double costGpt4oMiniUsd;
    }

}
