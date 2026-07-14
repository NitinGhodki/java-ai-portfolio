package com.aiportfolio.day9.router;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.regex.Pattern;

/**
 * QueryClassifier — determines query complexity to enable model routing.
 *
 * Complexity classification: SIMPLE, MODERATE, COMPLEX
 *
 * Why this matters:
 *   SIMPLE  → local Ollama model (free, instant, private)
 *   MODERATE→ HuggingFace (balanced)
 *   COMPLEX → best available model (GPT-4o or Mixtral)
 *
 * Classification signals:
 *   Length: longer queries are usually more complex
 *   Multi-step indicators: "and", "then", "also", "compare", "calculate"
 *   Reasoning indicators: "why", "explain", "analyse", "recommend"
 *   Math indicators: numbers + operators
 *   Simple indicators: single-fact questions, yes/no questions
 *
 * This is a rule-based classifier — fast and free.
 * In production you could use a tiny LLM for classification,
 * but that adds latency and cost to every request.
 * Rule-based is the right default: cheap, predictable, debuggable.
 *
 * Python equivalent: your supervisor_route() from Week 2 Day 13 —
 * deterministic routing without LLM involvement.
 */
@Slf4j
public class QueryClassifier {

    public enum Complexity { SIMPLE, MODERATE, COMPLEX }

    // Signals that increase complexity score
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "compare", "analyse", "analyze", "recommend", "explain why",
            "calculate", "difference between", "pros and cons",
            "step by step", "in detail", "comprehensive"
    );

    private static final List<String> MULTI_STEP_INDICATORS = List.of(
            " and ", " then ", " also ", " additionally ", " furthermore ",
            " first ", " second ", " third ", " finally "
    );

    private static final List<String> SIMPLE_PATTERNS_LIST = List.of(
            "what is the price", "how much does", "what does .* cost",
            "is .* available", "do you offer", "what is the",
            "how long is"
    );

    private static final Pattern MATH_PATTERN =
            Pattern.compile("\\d+[\\s]*[+\\-*/][\\s]*\\d+|\\d+%");

    /**
     * Classify query complexity.
     * Returns Complexity enum used by ModelRouter to select the right model.
     */
    public static Complexity classify(String query) {
        String lower = query.toLowerCase().trim();
        int score = 0;

        // Length scoring — longer queries tend to be more complex
        if (query.length() > 200) score += 3;
        else if (query.length() > 100) score += 2;
        else if (query.length() > 50) score += 1;

        // Complex keyword boost
        for (String keyword : COMPLEX_KEYWORDS) {
            if (lower.contains(keyword)) score += 2;
        }

        // Multi-step indicator boost
        for (String indicator : MULTI_STEP_INDICATORS) {
            if (lower.contains(indicator)) score += 1;
        }

        // Math detected — needs calculation capability
        if (MATH_PATTERN.matcher(query).find()) score += 2;

        // Question mark count — multiple questions = more complex
        long questionMarks = query.chars().filter(c -> c == '?').count();
        if (questionMarks > 1) score += 2;

        // Simple pattern detection — reduces score
        for (String pattern : SIMPLE_PATTERNS_LIST) {
            if (lower.matches(".*" + pattern + ".*")) score -= 2;
        }

        // Classify based on score
        Complexity result;
        if (score <= 1) result = Complexity.SIMPLE;
        else if (score <= 4) result = Complexity.MODERATE;
        else result = Complexity.COMPLEX;

        log.debug("[Classifier] Score={} Complexity={} Query={}",
                score, result, query.substring(0, Math.min(40, query.length())));
        return result;
    }

    /**
     * Get human-readable explanation of why a query was classified as it was.
     * Useful for debugging routing decisions.
     */
    public static String explain(String query) {
        String lower = query.toLowerCase();
        StringBuilder explanation = new StringBuilder();

        explanation.append(String.format("Length: %d chars%n", query.length()));

        for (String kw : COMPLEX_KEYWORDS) {
            if (lower.contains(kw)) explanation.append(String.format("Found complex keyword: '%s'%n", kw));
        }

        for (String indicator : MULTI_STEP_INDICATORS) {
            if (lower.contains(indicator)) explanation.append(String.format("Multi-step indicator: '%s'%n", indicator.trim()));
        }

        if (MATH_PATTERN.matcher(query).find()) {
            explanation.append("Contains mathematical expression%n");
        }

        long qCount = query.chars().filter(c -> c == '?').count();
        if (qCount > 1) explanation.append(String.format("Multiple questions: %d%n", qCount));

        explanation.append("Final classification: ").append(classify(query));
        return explanation.toString();
    }
}