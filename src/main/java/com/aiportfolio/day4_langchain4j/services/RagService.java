package com.aiportfolio.day4_langchain4j.services;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RagService — RAG pipeline using LangChain4j's EmbeddingStoreIngestor.
 *
 * LangChain4j RAG is different from Spring AI RAG:
 *
 * Spring AI:
 *   VectorStore.add(documents) ← you manage chunking separately
 *   QuestionAnswerAdvisor ← advisor pattern
 *
 * LangChain4j:
 *   EmbeddingStoreIngestor.ingest(document) ← handles chunking + embedding + storing
 *   ContentRetriever ← retrieves relevant segments
 *   AiServices.builder().contentRetriever(retriever) ← wires to service
 *
 * LangChain4j approach is more concise.
 * Spring AI approach is more configurable.
 *
 * Python equivalent:
 *   DocumentIngestionService = EmbeddingStoreIngestor
 *   VectorStore.search() = EmbeddingStoreContentRetriever.retrieve()
 *   QuestionAnswerAdvisor = ContentRetriever in AiServices
 */

@Slf4j
@Service
public class RagService {

    /**
     * Local embedding model — runs in-process, no API call.
     * all-MiniLM-L6-v2 bundled as a JAR dependency.
     * Free, fast, 384 dimensions.
     * Same model you used in Python via sentence-transformers.
     *
     * This is a key Java advantage: embed locally without network calls.
     * Python sentence-transformers also runs locally, but setup is heavier.
     */
    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final ChatModel chatModel;

    // The RAG-enabled chat service — built lazily after documents are ingested
    private RagChatService ragChatService;

    public RagService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    // ── AiService interface for RAG

    @SystemMessage("""
            Answer questions using ONLY the information from the provided documents.
            If the answer is not in the documents, say exactly:
            "I don't have that information in my documents."
            Do not use outside knowledge.
            """)
    interface RagChatService {
        String answer(@MemoryId String sessionId, @UserMessage String question);
    }

    // ── Ingestion

    /**
     * Ingest text into the embedding store.
     *
     * LangChain4j EmbeddingStoreIngestor pipeline:
     *   1. DocumentSplitter chunks the document
     *   2. EmbeddingModel embeds each chunk (LOCAL — no API call)
     *   3. EmbeddingStore stores (chunk_text, embedding_vector) pairs
     *
     * All three steps in one .ingest() call.
     * Compare to Spring AI where you call splitter.apply() then store.add() separately.
     */
    public int ingest(String text, String docName) {
        log.info("[RAG Ingest] Processing: {}", docName);

        // Create LangChain4j Document
        Document document = Document.from(text,
                dev.langchain4j.data.document.Metadata.from("source", docName)
        );

        // Split — 300 tokens per chunk, 30 overlap
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
        List<TextSegment> segments = splitter.split(document);
        log.info("[RAG Ingest] Split into {} segments", segments.size());

        // Embed each segment locally and store
        segments.forEach(segment -> {
            Embedding embedding = embeddingModel.embed(segment).content();
            embeddingStore.add(embedding, segment);
        });

        log.info("[RAG Ingest] Stored {} segments", segments.size());

        // Rebuild RAG service with updated store
        buildRagService();

        return segments.size();
    }

    private void buildRagService() {
        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.3)
                .build();

        ragChatService = AiServices.builder(RagChatService.class)
                .chatModel(chatModel)
                .contentRetriever(retriever)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(10))
                .build();

        log.info("[RAG] Service rebuilt with updated embedding store");
    }

    // ── Query

    public String query(String question, String sessionId) {
        if (ragChatService == null) {
            return "No documents ingested yet. Please ingest documents first via POST /api/lc4j/rag/ingest";
        }
        log.info("[RAG Query] session={} question={}", sessionId, question);
        return ragChatService.answer(sessionId, question);
    }
}