package com.aiportfolio.day6.rag.advanced;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;

/**
 * QueryRewriteTransformer — LangChain4j's equivalent of Python's HyDE.
 *
 * QueryTransformer interface: takes a Query, returns Collection<Query>.
 * Returning MULTIPLE queries means: search with all of them, merge results.
 * This is "query expansion" — broader than HyDE but same underlying goal:
 * bridge the vocabulary gap between user phrasing and document phrasing.
 *
 * How it's wired into the RAG pipeline (in AdvancedRagService):
 *   RetrievalAugmentor.builder()
 *       .queryTransformer(new QueryRewriteTransformer(model))
 *       .contentRetriever(retriever)
 *       .build()
 *
 * Every query passes through this transformer BEFORE retrieval.
 * Structural guarantee again — cannot forget to apply query rewriting.
 */
@Slf4j
@RequiredArgsConstructor
public class QueryRewriteTransformer implements QueryTransformer {

    private final ChatModel model;

    @Override
    public Collection<Query> transform(Query query) {
        String original = query.text();
        log.info("[QueryTransform] Original: {}", original);

        // Generate a more document-like rephrasing — same goal as HyDE
        // but rewriting the QUERY itself rather than generating a fake answer
        String rewritePrompt = String.format("""
                Rewrite this user question as a more formal, document-style statement
                that would match how the answer might be phrased in official documentation.
                Return ONLY the rewritten version, nothing else.
                
                Question: %s
                Rewritten:""", original);

        String rewritten = model.chat(rewritePrompt).trim();
        log.info("[QueryTransform] Rewritten: {}", rewritten);

        // Return BOTH original and rewritten — search with both, merge results
        // This is safer than HyDE-only: if rewrite is bad, original still works
        return List.of(
                query,                                      // original query
                Query.from(rewritten, query.metadata())      // rewritten query
        );
    }
}