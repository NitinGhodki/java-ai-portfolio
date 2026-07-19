package com.aiportfolio.week_a.day4_langchain4j.services;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

@SystemMessage("You are a helpful assistant. Remember and reference previous conversation turns.")
public interface ConversationalService {

    /**
     * @MemoryId links this call to a specific conversation.
     * Same memoryId across calls = same conversation history.
     * Different memoryId = completely separate conversation.
     * <p>
     * In a web app: pass session ID or user ID as memoryId.
     */
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
