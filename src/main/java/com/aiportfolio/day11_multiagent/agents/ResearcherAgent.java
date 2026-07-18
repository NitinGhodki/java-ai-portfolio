package com.aiportfolio.day11_multiagent.agents;

import com.aiportfolio.day11_multiagent.state.WorkflowState;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ResearcherAgent — finds information from YOUR documents via RAG.
 *
 * Previous version: called LLM directly with the question.
 * Problem: LLM answered from training knowledge, not your documents.
 *
 * Fixed version: attaches a ContentRetriever to the AiService.
 * LangChain4j automatically:
 *   1. Embeds the research task
 *   2. Retrieves top-k relevant chunks from EmbeddingStore
 *   3. Injects chunks as context into the LLM prompt
 *   4. LLM answers using YOUR document content
 *
 * This is the correct production behaviour —
 * the Researcher's findings come from your knowledge base,
 * not from LLM hallucination.
 *
 * EmbeddingStore is shared: the same store that RagService
 * uses for ingestion is injected here for retrieval.
 * Ingest via /api/lc4j/rag/ingest → Researcher finds it automatically.
 */
@Slf4j
@Component
public class ResearcherAgent {

    @SystemMessage("""
            You are a Research Agent with access to a document knowledge base.
            Your ONLY job is to find and report facts FROM THE PROVIDED DOCUMENTS.
            
            Rules:
            - Answer ONLY from the document context provided
            - Report specific numbers, dates, prices, and terms exactly as stated
            - Do NOT format or summarise — report raw findings
            - If information is NOT in the documents, say exactly: "NOT FOUND IN DOCUMENTS"
            - Never use outside knowledge or training data
            """)
    interface ResearcherService {
        String research(@UserMessage String researchTask);
    }

    private final ResearcherService service;
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * Constructor injection:
     *   chatLanguageModel  — the LLM for generation
     *   embeddingStore     — shared store where your ingested docs live
     *
     * Why inject EmbeddingStore directly instead of through RagService?
     * RagService owns the store. We inject the same bean here so both
     * RagService (for direct RAG queries) and ResearcherAgent (for agent
     * workflow queries) read from the same document collection.
     * One ingest call → available everywhere. No duplicate ingestion.
     */
    public ResearcherAgent(
            ChatModel chatLanguageModel,
            EmbeddingStore<TextSegment> embeddingStore
    ) {
        this.embeddingStore = embeddingStore;

        // Local embedding model — same as RagService uses
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        // ContentRetriever: embeds query → searches EmbeddingStore → returns chunks
        // maxResults=5: retrieve top 5 most relevant chunks
        // minScore=0.3: ignore chunks below 30% similarity (same threshold as Day 4)
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.3)
                .build();

        // Wire ContentRetriever into the AiService
        // LangChain4j automatically retrieves + injects context on every call
        this.service = AiServices.builder(ResearcherService.class)
                .chatModel(chatLanguageModel)
                .contentRetriever(contentRetriever)
                .build();

        log.info("[ResearcherAgent] Initialised with RAG content retriever (maxResults=5, minScore=0.3)");
    }

    /**
     * Execute research task using document knowledge base.
     * Returns updated state with researchFindings from YOUR documents.
     */
    public WorkflowState execute(WorkflowState state) {
        log.info("[Researcher] Starting RAG-based research for: {}",
                state.getUserRequest().substring(0, Math.min(50, state.getUserRequest().length())));

        long start = System.currentTimeMillis();

        // Build a focused research task
        // More specific = better retrieval (same lesson as Python Day 5 prompt patterns)
        String task = String.format("""
                Find all information related to this topic in the documents:
                %s
                
                Report:
                - Exact prices, quantities, and timeframes mentioned
                - Specific policies and their conditions
                - Technical specifications and limits
                - Any comparisons or contrasts stated in the documents
                """,
                state.getUserRequest()
        );

        String findings = service.research(task);
        long latency = System.currentTimeMillis() - start;

        log.info("[Researcher] RAG research complete in {}ms. Findings: {}...",
                latency,
                findings.substring(0, Math.min(80, findings.length())));

        // Detect if retrieval found nothing — useful for debugging
        if (findings.toUpperCase().contains("NOT FOUND IN DOCUMENTS")) {
            log.warn("[Researcher] No relevant documents found for: {}", state.getUserRequest());
        }

        return state
                .withResearchFindings(findings)
                .withLogEntry(String.format(
                        "Researcher (RAG): %d chars in %dms", findings.length(), latency
                ));
    }
}