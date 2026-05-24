package com.aiportfolio.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PromptPatterns — Java equivalent of your Python Day 1 prompt_patterns.py
 *
 * Three patterns demonstrated on the same question.
 * Run all three, compare output length and quality.
 *
 * Python used f-strings for templates.
 * Spring AI uses PromptTemplate with {variable} placeholders.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class PromptPatterns {

    private final ChatModel chatModel;

    private static final String QUESTION = "Explain recursion to a 10-year-old.";

    /**
     * PATTERN 1: Zero-shot
     * Just the question. No examples. No special instructions.
     * Lowest token cost. Unpredictable output format.
     */
    public String zeroShot() {
        log.debug("Running zero-shot pattern");
        return ChatClient.create(chatModel)
                .prompt(QUESTION)
                .call()
                .content();
    }

    /**
     * PATTERN 2: Few-shot
     * Two examples of good simple explanations before the question.
     * Higher token cost. Consistent output format.
     *
     * Spring AI PromptTemplate: uses {variable} placeholders.
     * Map.of("question", QUESTION) fills the template.
     */
    public String fewShot() {
        String template = """
                Here are two examples of explaining complex ideas simply:
                
                Example 1:
                Concept: Gravity
                Explanation: Gravity is like an invisible magnet that pulls everything toward
                the ground. That is why when you drop a ball, it falls down and not up.
                
                Example 2:
                Concept: Photosynthesis
                Explanation: Plants eat sunlight! They take sunlight, water, and air and turn
                it into food so they can grow, just like you eat food to grow.
                
                Now explain this concept the same way:
                Concept: {question}
                Explanation:
                """;
        var promptTemplate = new PromptTemplate(template);
        var prompt = promptTemplate.create(Map.of("question", QUESTION));

        return ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();
    }

    /**
     * PATTERN 3: Chain-of-thought
     * Force the model to reason step by step before answering.
     * Highest token cost. Best for reasoning-heavy questions.
     *
     * When NOT to use CoT in production:
     * - Simple classification (yes/no, category)
     * - High-volume low-cost pipelines
     * - When latency matters more than accuracy
     */
    public String chainOfThought() {
        String template = """
                Think through this step by step before answering:
                1. What is the core idea of the concept?
                2. What everyday thing does a 15-year-old already understand that this is similar to?
                3. Now use that comparison to explain it simply.
                
                Question: {question}
                """;

        var promptTemplate = new PromptTemplate(template);
        var prompt = promptTemplate.create(Map.of("question", QUESTION));

        return ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();
    }

    /**
     * Run all three patterns and return comparison results.
     */
    public PatternComparisonResult compareAll() {
        log.info("Running all three prompt patterns for comparison...");

        long start = System.currentTimeMillis();
        String zsResponse = zeroShot();
        long zsLatency = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        String fsResponse = fewShot();
        long fsLatency = System.currentTimeMillis() - start;

        start = System.currentTimeMillis();
        String cotResponse = chainOfThought();
        long cotLatency = System.currentTimeMillis() - start;

        return PatternComparisonResult.builder()
                .zeroShot(zsResponse)
                .zeroShotLatencyMs(zsLatency)
                .zeroShotApproxTokens(TokenUtils.approximateTokens(zsResponse))
                .fewShot(fsResponse)
                .fewShotLatencyMs(fsLatency)
                .fewShotApproxTokens(TokenUtils.approximateTokens(fsResponse))
                .chainOfThought(cotResponse)
                .chainOfThoughtLatencyMs(cotLatency)
                .chainOfThoughtApproxTokens(TokenUtils.approximateTokens(cotResponse))
                .build();
    }

    @lombok.Builder
    @lombok.Data
    public static class PatternComparisonResult {
        private String zeroShot;
        private long zeroShotLatencyMs;
        private int zeroShotApproxTokens;
        private String fewShot;
        private long fewShotLatencyMs;
        private int fewShotApproxTokens;
        private String chainOfThought;
        private long chainOfThoughtLatencyMs;
        private int chainOfThoughtApproxTokens;
    }

}
