package com.aiportfolio.week_a.Day2.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RagPipeline — the RETRIEVAL + GENERATION half of RAG.
 *
 * Python equivalent: your RAGPipeline class from Week 1 Day 3/4.
 *
 * Two query modes:
 *
 * Mode 1 — QuestionAnswerAdvisor (simple, recommended):
 *   Spring AI handles everything automatically.
 *   One line of code does: embed query → search → build prompt → generate.
 *   Equivalent to your Python LCEL chain from Day 4.
 *
 * Mode 2 — Manual retrieval + generation (for control):
 *   You do retrieval yourself, build prompt yourself, call LLM.
 *   Equivalent to your Python manual_rag.py from Day 3.
 *   Use this when you need custom retrieval logic (hybrid search, reranking).
 *
 * Understanding the difference matters for interviews.
 * "When would you NOT use QuestionAnswerAdvisor?"
 * Answer: when you need hybrid BM25+vector search, reranking,
 *         or custom context compression — all require manual mode.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class RagPipeline {

    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    // Mode 1: Advisor-based RAG (simple)
    /**
     * Query using QuestionAnswerAdvisor.
     * Spring AI automatically: retrieves context → builds prompt → generates answer.
     *
     * topK: how many chunks to retrieve (equivalent to Python's top_k parameter)
     *
     * The advisor injects this system prompt automatically:
     * "Use the following context to answer the question.
     *  Context: {context}
     *  If the context doesn't contain the answer, say you don't know."
     */

    private static final String STRICT_RAG_PROMPT = """
        Use ONLY the information provided in the CONTEXT below to answer the question.
        If the answer is not found in the CONTEXT, respond with exactly:
        "I don't have that information in my documents."
        Do NOT use any outside knowledge or training data.
        Do NOT guess or infer beyond what the context states.
        
        CONTEXT:
        {question_answer_context}
        """;

    public RagResponse queryWithAdvisor(String question, int topK) {
        log.info("[RAG Advisor] Query: {}", question);
        long start = System.currentTimeMillis();

        String answer = ChatClient.create(chatModel)
                .prompt()
                .system(STRICT_RAG_PROMPT)
                .advisors(new QuestionAnswerAdvisor(
                        vectorStore,
                        SearchRequest.builder().topK(topK).build()
                ))
                .user(question)
                .call()
                .content();

        long latencyMs = System.currentTimeMillis() - start;
        log.info("[RAG Advisor] Complete in {}ms", latencyMs);

        // Also get sources for citation (separate search call)
        List<Document> sources = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(topK).build()
        );

        return new RagResponse(question, answer, extractSources(sources), latencyMs, "advisor");
    }

    // Mode 2: Manual retrieval + generation (full control)
    /**
     * Query with manual control over every step.
     * Use this when you need to inspect, filter, or modify retrieved chunks.
     *
     * Steps:
     * 1. Retrieve top-k chunks using semantic search
     * 2. Format chunks into context string
     * 3. Build prompt with context injected
     * 4. Call LLM
     * 5. Return answer + sources
     *
     * Python equivalent: your RAGPipeline.query() method from Day 3
     */

    public RagResponse queryManual(String question, int topK) {
        log.info("[RAG Manual] Query: {}", question);
        long start = System.currentTimeMillis();

        // Step 1: Retrieve — VectorStore embeds query and searches
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(0.3)
                .build();

        List<Document> retrievedDocs = vectorStore.similaritySearch(searchRequest);
        log.info("[RAG Manual] Retrieved {} chunks", retrievedDocs.size());

        if (retrievedDocs.isEmpty()) {
            return new RagResponse(
                    question,
                    "I don't have enough information to answer this question.",
                    List.of(),
                    System.currentTimeMillis() - start,
                    "manual"
            );
        }

        // Step 2: Format context — same as Python format_docs()
        String context = retrievedDocs.stream()
                .map(doc -> String.format("[Source: %s]\n%s",
                        String.valueOf(doc.getMetadata().getOrDefault("source", "unknown")),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // Step 3: Build prompt — same as Python build_prompt()
        String prompt = String.format("""
                Answer the question using ONLY the context below.
                If the answer is not in the context, say "I don't have that information."
                Do not use outside knowledge.
                
                CONTEXT:
                %s
                
                QUESTION: %s
                
                ANSWER:
                """, context, question);

        // Step 4: Generate — plain LLM call with augmented prompt
        String answer = ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();

        long latencyMs = System.currentTimeMillis() - start;

        // Step 5: Return with sources
        return new RagResponse(question, answer, extractSources(retrievedDocs), latencyMs, "manual");
    }

    /**
     * Metadata-filtered retrieval — search only within a specific category.
     *
     * Python equivalent: ChromaDB's where={"category": "technical"} filter
     * Spring AI filter expression syntax: "category == 'technical'"
     *
     * This is how multi-document RAG works —
     * same question, different results based on which documents you search.
     */

    public RagResponse queryWithFilter(String question, String category, int topK) {
        log.info("[RAG Filter] Query: {} | category: {}", question, category);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression("category == '" + category + "'")
                .build();

        List<Document> docs = vectorStore.similaritySearch(searchRequest);

        if (docs.isEmpty()) {
            return new RagResponse(
                    question,
                    "No relevant information found in category: " + category,
                    List.of(), 0, "filtered"
            );
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String prompt = String.format(
                "Answer using only this context:\n%s\n\nQuestion: %s\nAnswer:",
                context, question
        );

        String answer = ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();

        return new RagResponse(question, answer, extractSources(docs), 0, "filtered-" + category);
    }

    // Helper methods
    private List<String> extractSources(List<Document> docs) {
        return docs.stream()
                .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
                .distinct()
                .collect(Collectors.toList());
    }

    // Response record

    /**
     * Java record — equivalent to Python dataclass or Pydantic model.
     * Immutable, auto-generates equals/hashCode/toString.
     * @param mode "advisor" or "manual" — shows which pipeline was used
     */
    public record RagResponse(
            String question,
            String answer,
            List<String> sources,
            long latencyMs,
            String mode
    ) {}

}
