package com.aiportfolio.day4_langchain4j.services;

import dev.langchain4j.service.SystemMessage;

/**
 * AiService interfaces — the core of LangChain4j.
 * <p>
 * You declare WHAT you want. LangChain4j implements HOW.
 * These are NOT concrete classes — they are interfaces.
 * LangChain4j generates the implementation at runtime.
 *
 * @SystemMessage — sets the system prompt for this service.
 * Applied to every call automatically.
 * Variables in {braces} are filled from method params.
 * @UserMessage — formats the user message.
 * Variables in {braces} map to method parameter names.
 * Without this: the String parameter IS the user message.
 * @MemoryId — identifies which conversation memory to use.
 * Different IDs = different memory stores = isolated users.
 * LangChain4j calls chatMemoryProvider(memoryId) to get
 * the right memory for this conversation.
 * <p>
 * Return type determines output format:
 * String      → plain text response
 * int/double  → numeric extraction from LLM response
 * Custom POJO → structured extraction (Day 5 topic)
 * TokenStream → streaming (advanced)
 */

// ── Interface 1: Basic chat ────────────────────────────────────────────────

@SystemMessage("You are a helpful, friendly assistant. Keep responses concise.")
public interface BasicChatService {
    public String chat(String userMessage);
}
