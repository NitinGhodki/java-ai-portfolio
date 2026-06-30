package com.aiportfolio.day4_langchain4j.services;

import com.aiportfolio.day4_langchain4j.tools.AgentTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


/**
 * AiServiceConfig — creates LangChain4j AiService beans.
 *
 * AiServices.builder() is LangChain4j's core factory.
 * It reads your interface and generates an implementation
 * at runtime using Java dynamic proxies.
 *
 * What you declare:                What LangChain4j generates:
 * interface + @SystemMessage   →   prompt template
 * method parameters            →   user message construction
 * return type                  →   response parsing (String, POJO, etc.)
 * .tools(agentTools)           →   tool registration + execution loop
 * .chatMemory(...)             →   conversation history management
 *
 * You never write the implementation.
 * This is the most powerful LangChain4j pattern.
 */

@Configuration
public class AiServiceConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    @Value("${spring.ai.openai.chat.options.temperature}")
    private Double temperature;

    // We name this bean explicitly to avoid any conflict with Spring AI
    @Bean(name = "langchain4jChatModel")
    public ChatModel langchain4jChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                // HuggingFace router requires the /v1 suffix for OpenAI compatibility
                .baseUrl(baseUrl + "/v1")
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .build();
    }
    /**
     * Basic chat service — no tools, no memory, no RAG.
     * Simplest possible AiService.
     */

    @Bean
    public BasicChatService basicChatService(ChatModel model) {
        return AiServices.create(BasicChatService.class, model);
    }

    /**
     * Agent service — has tools, no memory.
     * Uses AgentTools: calculator, date, word count, currency.
     */
    @Bean
    public AgentService agentService(ChatModel model, AgentTools tools) {
        return AiServices.builder(AgentService.class)
                .chatModel(model)
                .tools(tools)
                .build();
    }

    /**
     * Conversational service — no tools, HAS memory.
     * Memory is per conversationId — each user gets isolated history.
     *
     * MessageWindowChatMemory.withMaxMessages(10):
     *   Keeps last 10 messages (5 turns: 5 user + 5 assistant).
     *   Equivalent to Python's history[-5:] slice.
     */
    @Bean
    public ConversationalService conversationalService(ChatModel model) {
        return AiServices.builder(ConversationalService.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(10)
                )
                .build();
    }

    /**
     * Full agent — tools + memory.
     * Most capable. Use for complex multi-turn agent interactions.
     */
    @Bean
    public FullAgentService fullAgentService(ChatModel model, AgentTools tools) {
        return AiServices.builder(FullAgentService.class)
                .chatModel(model)
                .tools(tools)
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(10)
                )
                .build();
    }
}
