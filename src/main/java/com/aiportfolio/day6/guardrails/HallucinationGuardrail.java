package com.aiportfolio.day6.guardrails;

import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.data.message.AiMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * HallucinationGuardrail — OUTPUT guardrail.
 * Checks the LLM's response AFTER generation, before returning to caller.
 *
 * This is a simple heuristic check — production systems would use
 * a faithfulness scoring model (like RAGAS faithfulness, Python Week 2 Day 10)
 * but that requires the original context, which output guardrails
 * don't have direct access to in this simple form.
 *
 * What this catches: responses that hedge excessively or contain
 * phrases indicating the model is uncertain or making things up.
 *
 * OutputGuardrailResult.success()   → pass through
 * OutputGuardrailResult.failure()   → triggers automatic RETRY
 *                                      LangChain4j re-prompts the LLM
 *                                      with the failure reason included
 */
@Slf4j
public class HallucinationGuardrail implements OutputGuardrail {

    private static final List<String> UNCERTAINTY_MARKERS = List.of(
            "i'm not sure but",
            "i think it might be",
            "as far as i know",  // can indicate model is guessing
            "i believe, though i'm not certain"
    );

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text().toLowerCase();

        for (String marker : UNCERTAINTY_MARKERS) {
            if (text.contains(marker)) {
                log.warn("[Guardrail: Hallucination] Uncertainty marker found: '{}'", marker);
                return failure("Response contains uncertainty markers suggesting low confidence. " +
                        "Please provide a more definitive answer based only on verified information, " +
                        "or state clearly that the information is not available.");
            }
        }

        // Check for suspiciously short responses to complex-seeming questions
        if (text.trim().length() < 10) {
            log.warn("[Guardrail: Hallucination] Response suspiciously short: '{}'", text);
            return failure("Response is too short to be useful. Please provide a complete answer.");
        }

        return OutputGuardrailResult.success();
    }
}
