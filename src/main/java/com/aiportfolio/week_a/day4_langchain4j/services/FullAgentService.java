package com.aiportfolio.week_a.day4_langchain4j.services;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

@SystemMessage("""
        You are a knowledgeable assistant with tools and conversation memory.
        Use calculator for any numeric computation.
        Use date tool when current date or year is needed.
        Use currency converter when amounts need conversion.
        Reference previous conversation context when relevant.
        """)
public interface FullAgentService {
    String query(@MemoryId String sessionId, @UserMessage String userMessage);
}