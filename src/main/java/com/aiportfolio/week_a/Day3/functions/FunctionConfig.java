package com.aiportfolio.week_a.Day3.functions;

import lombok.extern.slf4j.Slf4j;
//import org.apache.el.lang.ExpressionBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * FunctionConfig — registers Java methods as LLM-callable tools.
 *
 * Each @Bean that implements Function<Input, Output> becomes a tool.
 * @Description on the @Bean = the tool description sent to the LLM.
 *
 * Spring AI wires everything:
 * 1. Reads the @Description annotation
 * 2. Reads the Input record's @JsonPropertyDescription annotations
 * 3. Builds a JSON schema and sends it to the LLM
 * 4. When LLM calls the function, Spring AI deserialises arguments
 *    into the Input record and calls your Function.apply()
 *
 * You write zero parsing, zero serialisation, zero loop code.
 * Just the business logic inside apply().
 *
 * Python equivalent:
 *   @tool
 *   def calculator(expression: str) -> str:
 *       ...
 */

@Slf4j
@Configuration
public class FunctionConfig {

    // ── Function 1: Calculator
    @Bean
    @Description("Evaluate a mathematical expression and return the numeric result")
    public Function<FunctionSchemas.CalculatorRequest, FunctionSchemas.CalculatorResponse>
    calculatorFunction() {
        return request -> {
            String expression = request.expression().trim();
            log.info("[Function: calculator] expression={}", expression);

            try {
                // Use JavaScript engine for safe eval
                // ScriptEngine is available in Java 11+
                // For production: use exp4j library instead
//                var engine = new javax.script.ScriptEngineManager()
//                        .getEngineByName("JavaScript");
//                Object rawResult = engine.eval(expression);
//                double result = Double.parseDouble(rawResult.toString());
                if (expression.matches(".*[a-zA-Z]{3,}.*")) {
                    throw new IllegalArgumentException("Expression contains text words, not mathematical numbers.");
                }
                double result = new ExpressionBuilder(expression).build().evaluate();
                String formatted = String.format("%.4f", result);

                log.info("[Function: calculator] result={}", formatted);
                return new FunctionSchemas.CalculatorResponse(
                        expression,
                        formatted,
                        String.format("%s = %s", expression, formatted)
                );
            } catch (Exception e) {
                log.error("[Function: calculator] Error: {}", e.getMessage());
                return new FunctionSchemas.CalculatorResponse(
                        expression,
                        "Error: " + e.getMessage(),
                        "Could not evaluate: " + expression
                );
            }
        };
    }

    // ── Function 2: Date
    @Bean
    @Description("Get the current date, time, day of week, and year")
    public Function<FunctionSchemas.DateRequest, FunctionSchemas.DateResponse>
    dateFunction() {
        return request -> {
            log.info("[Function: date] called");
            LocalDateTime now = LocalDateTime.now();

            String formatPattern = (request.format() != null && !request.format().isBlank())
                    ? request.format()
                    : "yyyy-MM-dd HH:mm:ss";

            return new FunctionSchemas.DateResponse(
                    now.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    now.getDayOfWeek().toString(),
                    now.getYear(),
                    now.format(DateTimeFormatter.ofPattern(formatPattern))
            );
        };
    }

    // ── Function 3: Word Counter
    @Bean
    @Description("Count words and characters in a text string")
    public Function<FunctionSchemas.WordCountRequest, FunctionSchemas.WordCountResponse>
    wordCountFunction() {
        return request -> {
            String text = request.text();
            log.info("[Function: wordCount] text length={}", text.length());

            int words = text.isBlank() ? 0 : text.trim().split("\\s+").length;
            int chars = text.length();
            int charsNoSpaces = text.replace(" ", "").length();

            return new FunctionSchemas.WordCountResponse(words, chars, charsNoSpaces);
        };
    }

    // ── Function 4: Currency Converter
    @Bean
    @Description("Convert currency amounts between INR, USD, EUR, and GBP")
    public Function<FunctionSchemas.CurrencyRequest, FunctionSchemas.CurrencyResponse>
    currencyFunction() {
        // Approximate rates — in production call a real rates API
        Map<String, Double> toUsd = Map.of(
                "INR", 1.0 / 93.5,
                "USD", 1.0,
                "EUR", 1.08,
                "GBP", 1.27
        );

        return request -> {
            log.info("[Function: currency] {} {} to {}",
                    request.amount(), request.fromCurrency(), request.toCurrency());

            Double fromRate = toUsd.get(request.fromCurrency().toUpperCase());
            Double toRate = toUsd.get(request.toCurrency().toUpperCase());

            if (fromRate == null || toRate == null) {
                return new FunctionSchemas.CurrencyResponse(
                        request.amount(), request.fromCurrency(),
                        0, request.toCurrency(), 0,
                        "Unsupported currency. Use INR, USD, EUR, or GBP."
                );
            }

            double usdAmount = request.amount() * fromRate;
            double converted = usdAmount / toRate;
            double rate = fromRate / toRate;

            return new FunctionSchemas.CurrencyResponse(
                    request.amount(),
                    request.fromCurrency(),
                    Math.round(converted * 100.0) / 100.0,
                    request.toCurrency(),
                    Math.round(rate * 10000.0) / 10000.0,
                    "Approximate rate — for reference only"
            );
        };
    }
}
