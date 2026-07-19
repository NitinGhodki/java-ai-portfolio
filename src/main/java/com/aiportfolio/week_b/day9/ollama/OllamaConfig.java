package com.aiportfolio.week_b.day9.ollama;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * OllamaConfig — manual configuration for two simultaneous model providers.
 *
 * Why manual configuration instead of auto-configuration?
 * Spring AI's auto-configuration creates ONE ChatModel bean from
 * the primary provider (OpenAI/HuggingFace in your case).
 * To have TWO ChatModel beans (Ollama + HuggingFace) simultaneously,
 * you must configure them manually and give them distinct bean names.
 *
 * @Primary marks the HuggingFace model as the default —
 * any @Autowired ChatModel without a qualifier gets HuggingFace.
 * Ollama is injected explicitly using @Qualifier("ollamaChatModel").
 *
 * This is the same multi-provider pattern used by enterprise teams
 * that need: local model for sensitive data + API model for complex tasks.
 */
@Configuration
public class OllamaConfig {

    @Value("${spring.ai.openai.api-key}")
    private String hfApiKey;

    @Value("${spring.ai.openai.base-url}")
    private String hfBaseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String hfModel;

    @Value("${spring.ai.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String ollamaModel;

    /**
     * HuggingFace/OpenAI model — API-based, for complex queries.
     * @Primary = default when no qualifier specified.
     */
    @Bean("huggingFaceChatModel")
    @Primary
    public OpenAiChatModel huggingFaceChatModel() {
        var api = new OpenAiApi(hfBaseUrl, hfApiKey);
        var options = OpenAiChatOptions.builder()
                .model(hfModel)
                .maxCompletionTokens(512)
                .temperature(0.7)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    /**
     * Ollama model — local, for simple queries and sensitive data.
     * Named "ollamaChatModel" for explicit injection via @Qualifier.
     */
    @Bean("ollamaChatModel")
    public OllamaChatModel ollamaChatModel() {
        var api = new OllamaApi(ollamaBaseUrl);
        var options = OllamaOptions.builder()
                .model(ollamaModel)
                .temperature(0.7)
                .build();
        return OllamaChatModel.builder().ollamaApi(api).defaultOptions(options).build();
    }
}