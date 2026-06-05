package com.aiportfolio.day4_langchain4j.services;

import dev.langchain4j.service.SystemMessage;

@SystemMessage("""
        You are a helpful assistant with access to tools.
        Use tools when they provide more accurate answers.
        Always use the calculator tool for math — never compute in your head.
        Always use the date tool when current date is needed.
        """)
public interface AgentService {
    String query(String userMessage);
}
