package com.aiportfolio.week_a.day1.llm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * LLMClient — Java equivalent of your Python Day 1 llm_client.py
 *
 * Python:                          Java equivalent:
 * client.chat(...)             →   llmClient.chat(...)
 * client.stream(...)           →   llmClient.stream(...) returns Flux<String>
 * client.withSystemPrompt(...) →   llmClient.withSystemPrompt(...)
 *
 * Key difference: Java uses reactive streams (Flux) for streaming.
 * Flux<String> = a stream of strings that arrives over time.
 * The caller subscribes to it and receives chunks as they arrive.
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class LLMClient {

    private final ChatModel chatModel;
    private String systemPrompt = null;

    /**
     * Single chat call — returns complete response as a String.
     * Equivalent to Python: client.chat(user_message)
     */
    public String chat(String userMessage) {
        log.debug("Calling LLM with message: {}", userMessage.substring(0, Math.min(50, userMessage.length())));

        var prompt = buildPrompt(userMessage);
        var response = ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();

        log.debug("LLM response: {}", response.substring(0, Math.min(80, response.length())));
        return response;
    }

    /**
     * Streaming call — returns Flux<String> of token chunks.
     * Equivalent to Python: client.stream(user_message)
     *
     * Flux is a reactive stream — it emits items over time.
     * Caller subscribes and receives each token as it arrives.
     * Perfect for Server-Sent Events (SSE) endpoints.
     */
    public Flux<String> stream(String userMessage) {
        log.debug("Starting streaming call for: {}", userMessage.substring(0, Math.min(50, userMessage.length())));

        var prompt = buildPrompt(userMessage);
        return ChatClient.create(chatModel)
                .prompt(prompt)
                .stream()
                .content();
    }

    /**
     * Returns a new LLMClient with a system prompt baked in.
     * Equivalent to Python: client.with_system_prompt("You are...")
     *
     * Why return a new instance?
     * Same reason as Python — immutability. The original client is unchanged.
     * You can create multiple specialised clients from one base.
     */
    public LLMClient withSystemPrompt(String systemPrompt) {
        var newClient = new LLMClient(chatModel);
        newClient.systemPrompt = systemPrompt;
        return newClient;
    }

    /**
     * Build prompt with optional system message.
     * If systemPrompt is set, includes it as the first message.
     */
    private Prompt buildPrompt(String userMessage) {
        if  (systemPrompt != null && !systemPrompt.isBlank()) {
            return new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
            ));
        }
        return new Prompt(new UserMessage(userMessage));
    }


}
