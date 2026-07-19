package com.aiportfolio.week_a.day6.guardrails;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * PiiGuardrail — detects and redacts personally identifiable information
 * before it reaches the LLM.
 *
 * Why this matters in production:
 * If a user pastes their credit card number or SSN into a chat,
 * you do NOT want that sent to a third-party LLM API.
 * This guardrail catches it BEFORE the API call — using successWith()
 * to redact rather than fully reject (better UX than blocking entirely).
 *
 * Patterns covered: credit card numbers, email addresses, phone numbers.
 * In production: use a proper PII detection library (Microsoft Presidio,
 * AWS Comprehend) instead of regex — regex misses many real-world formats.
 */
@Slf4j
public class PiiGuardrail implements InputGuardrail {

    private static final Pattern CREDIT_CARD = Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b\\d{10}\\b");

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText();
        String redacted = text;
        boolean foundPii = false;

        if (CREDIT_CARD.matcher(text).find()) {
            redacted = CREDIT_CARD.matcher(redacted).replaceAll("[REDACTED-CARD]");
            foundPii = true;
            log.warn("[Guardrail: PII] Credit card number redacted");
        }

        if (EMAIL.matcher(text).find()) {
            redacted = EMAIL.matcher(redacted).replaceAll("[REDACTED-EMAIL]");
            foundPii = true;
            log.warn("[Guardrail: PII] Email address redacted");
        }

        if (PHONE.matcher(text).find()) {
            redacted = PHONE.matcher(redacted).replaceAll("[REDACTED-PHONE]");
            foundPii = true;
            log.warn("[Guardrail: PII] Phone number redacted");
        }

        if (foundPii) {
            log.info("[Guardrail: PII] Input modified to remove PII before LLM call");
            return InputGuardrailResult.successWith(redacted);
        }

        return InputGuardrailResult.success();
    }
}
