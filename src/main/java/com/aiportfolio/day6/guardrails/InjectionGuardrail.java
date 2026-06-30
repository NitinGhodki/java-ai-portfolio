package com.aiportfolio.day6.guardrails;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * InjectionGuardrail — blocks prompt injection attempts.
 *
 * This is your Python Week 2 Day 5 detect_injection() function,
 * but wired into the framework instead of called manually.
 *
 * InputGuardrail interface — implement validate(), return a result:
 *   InputGuardrailResult.success()              → pass through unchanged
 *   InputGuardrailResult.successWith(newInput)  → pass through MODIFIED input
 *   InputGuardrailResult.failure(reason)        → REJECT, LLM never called
 *
 * Key advantage over Python: this class is reusable across
 * every AiService interface in your application. Write once,
 * apply everywhere via @InputGuardrails annotation.
 */
@Slf4j
public class InjectionGuardrail implements InputGuardrail {

    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "forget your instructions",
            "disregard your system prompt",
            "you are now",
            "pretend you are",
            "act as if you have no restrictions",
            "your new instructions",
            "override instructions",
            "jailbreak",
            "do anything now",
            "dan mode"
    );

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String text = userMessage.singleText().toLowerCase();

        for (String pattern : INJECTION_PATTERNS) {
            if (text.contains(pattern)) {
                log.warn("[Guardrail: Injection] BLOCKED — pattern matched: '{}'", pattern);
                return failure("Request blocked: potential prompt injection detected (pattern: " + pattern + ")");

            }
        }

        if (text.length() > 2000) {
            log.warn("[Guardrail: Injection] BLOCKED — input exceeds 2000 chars");
            return failure("Request blocked: input exceeds maximum length of 2000 characters");
        }

        log.debug("[Guardrail: Injection] PASSED");
        return InputGuardrailResult.success();
    }
}