package com.aiportfolio.day6.guardrails;

import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GuardedChatService — the interface with guardrails wired in.
 *
 * @InputGuardrails and @OutputGuardrails are method-level annotations.
 * Every call to chat() automatically runs through both guardrails,
 * in the order: InjectionGuardrail → PiiGuardrail (input)
 *                                  → LLM call
 *                                  → HallucinationGuardrail (output)
 *
 * If InjectionGuardrail fails: LLM is never called. Zero cost.
 * If PiiGuardrail modifies input: LLM sees redacted version.
 * If HallucinationGuardrail fails: LangChain4j retries the LLM call
 *   automatically with the failure reason as feedback (default 2 retries).
 *
 * This is structurally impossible to bypass — every caller of chat()
 * gets the same protection. Compare to Python where each function
 * needs to remember to call detect_injection() manually.
 */
public class GuardedChatService {

    @SystemMessage("""
            You are a customer support assistant for TechCorp.
            Answer questions clearly and factually.
            If you don't know something, say so directly without hedging.
            """)
    public interface GuardedAgent {

        @InputGuardrails({InjectionGuardrail.class, PiiGuardrail.class})
        @OutputGuardrails(HallucinationGuardrail.class)
        String chat(String message);
    }

    @Configuration
    public static class Config {
        @Bean
        public GuardedAgent guardedAgent(ChatModel model) {
            return AiServices.create(GuardedAgent.class, model);
        }
    }
}
