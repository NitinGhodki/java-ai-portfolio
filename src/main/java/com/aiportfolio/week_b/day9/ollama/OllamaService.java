package com.aiportfolio.week_b.day9.ollama;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * OllamaService — direct Ollama operations.
 *
 * Uses the local Ollama model exclusively.
 * Inject via @Qualifier("ollamaChatModel") — must be explicit
 * because @Primary on HuggingFace model means unqualified
 * injection always gets HuggingFace.
 *
 * Three use cases demonstrated:
 * 1. Simple chat — fast local inference
 * 2. Streaming — same Flux<String> pattern as HuggingFace
 * 3. Sensitive data processing — data stays on machine
 */
@Slf4j
@Service
public class OllamaService {

    private final ChatModel ollamaModel;

    public OllamaService(@Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        this.ollamaModel = ollamaModel;
    }

    /**
     * Direct chat with local Ollama model.
     * Data never leaves your machine.
     */
    public String chat(String message) {
        log.info("[Ollama] Local inference: {}...",
                message.substring(0, Math.min(50, message.length())));
        long start = System.currentTimeMillis();

        String response = ChatClient.create(ollamaModel)
                .prompt(message)
                .call()
                .content();

        log.info("[Ollama] Response in {}ms", System.currentTimeMillis() - start);
        return response;
    }

    /**
     * Streaming with Ollama.
     * Same Flux<String> API as HuggingFace — code is identical.
     * This proves Spring AI's provider abstraction works.
     */
    public Flux<String> stream(String message) {
        return ChatClient.create(ollamaModel)
                .prompt(message)
                .stream()
                .content();
    }

    /**
     * Process sensitive data locally.
     * Explicit log message reminds you this stays on-machine.
     */
    public String processSensitiveData(String sensitiveContent, String instruction) {
        log.info("[Ollama] Processing sensitive data locally — no external API call");

        String prompt = String.format("""
                %s
                
                Data to process:
                %s
                """, instruction, sensitiveContent);

        return ChatClient.create(ollamaModel)
                .prompt()
                .system("You are processing sensitive data. Be precise and concise.")
                .user(prompt)
                .call()
                .content();
    }

    /**
     * Health check — verify Ollama is running before routing to it.
     * Returns true if local model responds within 10 seconds.
     */
    public boolean isHealthy() {
        try {
            String response = ChatClient.create(ollamaModel)
                    .prompt("Reply with exactly: OK")
                    .call()
                    .content();
            return response != null && !response.isBlank();
        } catch (Exception e) {
            log.warn("[Ollama] Health check failed: {}", e.getMessage());
            return false;
        }
    }
}