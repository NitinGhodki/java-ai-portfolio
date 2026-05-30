package com.aiportfolio.Day3.functions;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * FunctionSchemas — input/output records for each function.
 *
 * Spring AI reads these records to build JSON schemas automatically.
 * The LLM reads those schemas to know: what arguments to pass,
 * what data types to use, and what each field means.
 *
 * @JsonClassDescription  → describes the function to the LLM
 * @JsonPropertyDescription → describes each argument to the LLM
 *
 * These descriptions ARE your prompt for tool selection.
 * Vague descriptions = LLM calls wrong tools.
 * Precise descriptions = LLM calls right tools every time.
 * Same lesson as Python's @tool docstring from Week 2 Day 13.
 */

public class FunctionSchemas {

    // ── Calculator
    @JsonClassDescription("Evaluate a mathematical expression. Use for any arithmetic, percentage, or numeric calculation.")
    public record CalculatorRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("A valid mathematical expression like '2999 * 0.8' or '15000 * 0.23'")
            String expression
    ) {}

    public record CalculatorResponse(
            String expression,
            String result,
            String formattedResult
    ) {}

    // ── Date/Time

    @JsonClassDescription("Get the current date and time. Use when the question involves today's date, current year, or day of the week.")
    public record DateRequest(
            @JsonProperty(required = false)
            @JsonPropertyDescription("Optional: format string like 'yyyy-MM-dd'. Leave empty for default format.")
            String format
    ) {}

    public record DateResponse(
            String currentDate,
            String dayOfWeek,
            int year,
            String formattedDate
    ) {}

    // ── Word counter
    @JsonClassDescription("Count the number of words and characters in a text. Use when asked about text length or word count.")
    public record WordCountRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The text to count words and characters in")
            String text
    ) {}

    public record WordCountResponse(
            int wordCount,
            int characterCount,
            int characterCountNoSpaces
    ) {}

    // ── Currency converter
    @JsonClassDescription("Convert between currencies using approximate rates. Use when asked to convert rupees to USD, EUR, or GBP.")
    public record CurrencyRequest(
            @JsonProperty(required = true)
            @JsonPropertyDescription("The amount to convert as a number")
            double amount,

            @JsonProperty(required = true)
            @JsonPropertyDescription("Source currency code: INR, USD, EUR, or GBP")
            String fromCurrency,

            @JsonProperty(required = true)
            @JsonPropertyDescription("Target currency code: INR, USD, EUR, or GBP")
            String toCurrency
    ) {}

    public record CurrencyResponse(
            double originalAmount,
            String fromCurrency,
            double convertedAmount,
            String toCurrency,
            double exchangeRate,
            String note
    ) {}


}
