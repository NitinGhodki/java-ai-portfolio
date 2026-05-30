package com.aiportfolio.Day3.advisor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AdvisorPipeline — demonstrates advisor chaining.
 *
 * Three pipelines built today:
 *
 * Pipeline 1: Function calling only
 *   ChatClient with 4 registered functions.
 *   LLM decides which function to call based on the question.
 *
 * Pipeline 2: RAG + Memory (no functions)
 *   QuestionAnswerAdvisor: adds document context to every query
 *   MessageChatMemoryAdvisor: remembers conversation history
 *   One ChatClient handles both automatically.
 *
 * Pipeline 3: Functions + RAG + Memory (full production pipeline)
 *   All three combined. The most capable but most expensive.
 *   Use for: complex domain assistants that need documents + tools + history.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorPipeline {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    /**
     * Session memory store — one InMemoryChatMemory per session ID.
     * ConcurrentHashMap = thread-safe for concurrent requests.
     *
     * Python equivalent: your {session_id: agent} dict from Week 2 Day 7.
     * Key difference: Java's ConcurrentHashMap handles concurrent access
     * safely without extra locking code.
     */

    private final Map<String, InMemoryChatMemory> sessionMemories =
            new ConcurrentHashMap<>();

    private InMemoryChatMemory getOrCreateMemory(String sessionId) {
        return sessionMemories.computeIfAbsent(sessionId, k -> new InMemoryChatMemory());
    }

    // ── Pipeline 1: Function calling
    /**
     * Agent with 4 tools. LLM decides which to call.
     *
     * .functions("beanName") — registers a @Bean function by its bean name.
     * Bean name = method name in @Configuration class by default.
     * "calculatorFunction" = the @Bean method name in FunctionConfig.
     *
     * Spring AI sends function schemas to LLM.
     * LLM returns tool call → Spring AI executes @Bean → sends result back.
     * You write zero loop code.
     */

    public String queryWithFunctions(String question) {
        log.info("[Functions] Query: {}", question);

        return ChatClient.create(chatModel)
                .prompt()
                .system("""
                        You are a helpful assistant with access to tools.
                        Use tools when they would give a more accurate answer.
                        Do Not use your own knowledge data.
                        """)
                .user(question)
                .functions(
                        "calculatorFunction",
                        "dateFunction",
                        "wordCountFunction",
                        "currencyFunction"
                )
                .call()
                .content();
    }

    // ── Pipeline 2: RAG + Memory
    /**
     * Advisor chain: memory → RAG → LLM
     *
     * Execution order:
     * 1. MessageChatMemoryAdvisor.before(): adds previous messages to prompt
     * 2. QuestionAnswerAdvisor.before(): adds retrieved document context
     * 3. LLM processes: question + history + context
     * 4. QuestionAnswerAdvisor.after(): nothing (RAG has no post-processing)
     * 5. MessageChatMemoryAdvisor.after(): saves new message pair to memory
     *
     * WINDOW_SIZE = 10: keep last 10 messages in memory.
     * Higher = more context but more tokens per call.
     * In production: set based on your token budget per session.
     *
     * conversationId links the memory to a specific session.
     * Different users = different conversationIds = isolated memories.
     */

    private static final int WINDOW_SIZE = 10;

    private static final String RAG_MEMORY_SYSTEM_PROMPT = """
            You are a helpful assistant with access to documents and conversation history.
            Answer questions using ONLY the provided document context.
            If the answer is not in the documents, say "I don't have that information."
            You may reference previous conversation turns if relevant.
            
            {question_answer_context}
            """;

    public String queryWithRagAndMemory(String question, String sessionId) {
        log.info("[RAG+Memory] Session: {} | Query: {}", sessionId, question);

        var memory = getOrCreateMemory(sessionId);

        return ChatClient.create(chatModel)
                .prompt()
                .system(RAG_MEMORY_SYSTEM_PROMPT)
                .advisors(
                        // Order matters: memory first, then RAG
                        // Memory advisor runs first to inject history
                        // RAG advisor runs second to inject document context
                        MessageChatMemoryAdvisor.builder(memory)
                                .conversationId(sessionId)
                                .build(),
                        new QuestionAnswerAdvisor(
                                vectorStore,
                                SearchRequest.builder().topK(3).build()
                        )
                )
                .user(question)
                .call()
                .content();
    }

    // Pipeline 3: Functions + RAG + Memory (full pipeline)

    /**
     * All three combined.
     *
     * Use case: a domain expert assistant that:
     * - Remembers what was discussed (memory)
     * - Searches company documents for facts (RAG)
     * - Performs calculations and date operations (functions)
     *
     * This is your Python Week 2 ResearchAgent — but in Java,
     * with two lines of code instead of four files.
     */

    public String queryFull(String question, String sessionId) {
        log.info("[Full Pipeline] Session: {} | Query: {}", sessionId, question);

        var memory = getOrCreateMemory(sessionId);

        return ChatClient.create(chatModel)
                .prompt()
                .system("""
                        You are an expert assistant with documents, tools, and conversation history.
                        - Use document context for factual questions about policies and pricing
                        - Use calculator tool for any numeric calculations
                        - Use date tool when current date is needed
                        - Reference conversation history when relevant
                        - If information is not available, say so clearly
                        
                        {question_answer_context}
                        """)
                .advisors(
                        MessageChatMemoryAdvisor.builder(memory)
                                .conversationId(sessionId)
                                .build(),
                        new QuestionAnswerAdvisor(
                                vectorStore,
                                SearchRequest.builder().topK(3).build()
                        )
                )
                .functions(
                        "calculatorFunction",
                        "dateFunction",
                        "currencyFunction"
                )
                .user(question)
                .call()
                .content();
    }

    // Pipeline 4: Streaming with memory
    /**
     * Same RAG + memory pipeline but streaming.
     * Returns Flux<String> — tokens arrive one by one.
     *
     * Key: advisors work identically with stream() and call().
     * No code change needed to switch between streaming and non-streaming.
     */

    public Flux<String> streamWithMemory(String question, String sessionId) {
        log.info("[Stream+Memory] Session: {} | Query: {}", sessionId, question);

        var memory = getOrCreateMemory(sessionId);

        return ChatClient.create(chatModel)
                .prompt()
                .system(RAG_MEMORY_SYSTEM_PROMPT)
                .advisors(
                        MessageChatMemoryAdvisor.builder(memory)
                                .conversationId(sessionId)
                                .build(),
                        new QuestionAnswerAdvisor(
                                vectorStore,
                                SearchRequest.builder().topK(3).build()
                        )
                )
                .user(question)
                .stream()
                .content();
    }

    public void clearMemory(String sessionId) {
        sessionMemories.remove(sessionId);
        log.info("[Memory] Cleared session: {}", sessionId);
    }

    public int getMemorySize(String sessionId) {
        var memory = sessionMemories.get(sessionId);
        if (memory == null) return 0;
        return memory.get(sessionId, Integer.MAX_VALUE).size();
    }


}
