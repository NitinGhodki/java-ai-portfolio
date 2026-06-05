package com.aiportfolio.day4_langchain4j.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * AgentTools — tools the LLM can call.
 *
 * @Tool annotation is LangChain4j's equivalent of:
 *   Python:    @tool decorator
 *   Spring AI: @Bean Function<Input, Output>
 *
 * Key advantages over both:
 * 1. Return type is the actual Java type (double, not String)
 *    → LLM receives typed result, no parsing needed
 * 2. Method parameters map directly to LLM arguments
 *    → No separate Input record needed
 * 3. The @Tool description IS the tool prompt
 *    → Same lesson as Python docstrings: precise = better tool use
 *
 * This class is a @Component — Spring manages its lifecycle.
 * LangChain4j injects it into AiServices that declare tool usage.
 */

@Slf4j
@Component
public class AgentTools {

    @Tool("Evaluate a mathematical expression. " +
            "Use for arithmetic, percentages, and numeric calculations. " +
            "Input must be a valid expression like '2999 * 0.8' or '15000 * 0.23'.")
    public double calculate(String expression) {
        log.info("[Tool: calculate] expression={}", expression);
        try {
            // Clean Mistral's ReAct bleed (same issue as Python Day 6)
            String cleaned = expression.split("\n")[0].trim();
//            var engine = new javax.script.ScriptEngineManager()
//                    .getEngineByName("JavaScript");
//            Object result = engine.eval(cleaned);
            if (expression.matches(".*[a-zA-Z]{3,}.*")) {
                throw new IllegalArgumentException("Expression contains text words, not mathematical numbers.");
            }
            Object result = new ExpressionBuilder(cleaned).build().evaluate();
            double value = Double.parseDouble(result.toString());
            log.info("[Tool: calculate] result={}", value);
            return Math.round(value * 10000.0) / 10000.0;
        } catch (Exception e) {
            log.error("[Tool: calculate] failed: {}", e.getMessage());
            return Double.NaN;
        }
    }

    @Tool("Get the current date and day of week. " +
            "Use when the question involves today's date, current year, or day of week.")
    public String getCurrentDate() {
        log.info("[Tool: getCurrentDate] called");
        LocalDateTime now = LocalDateTime.now();
        return String.format("%s, %s",
                now.format(DateTimeFormatter.ISO_LOCAL_DATE),
                now.getDayOfWeek()
        );
    }

    @Tool("Count the number of words in a text string. " +
            "Use when asked to count words or measure text length.")
    public int countWords(String text) {
        log.info("[Tool: countWords] text length={}", text.length());
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }

    @Tool("Convert Indian Rupees (INR) to US Dollars (USD). " +
            "Use when asked to convert rupees to dollars. " +
            "Input: amount in rupees as a number.")
    public String convertInrToUsd(double amountInr) {
        log.info("[Tool: convertInrToUsd] amount={}", amountInr);
        double rate = 1.0 / 83.5;
        double usd = Math.round(amountInr * rate * 100.0) / 100.0;
        return String.format("%.2f INR = %.2f USD (rate: 1 USD ≈ 83.5 INR)", amountInr, usd);
    }

}
