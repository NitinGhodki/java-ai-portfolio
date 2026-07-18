package com.aiportfolio.day11_multiagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.List;
/**
 * EmbeddingStoreConfig — shared EmbeddingStore bean.
 *
 * Why this matters:
 * Before this change: RagService and ResearcherAgent each had their
 * own separate InMemoryEmbeddingStore. Ingesting via /api/lc4j/rag/ingest
 * added documents to RagService's store only. ResearcherAgent's store
 * stayed empty forever. Researcher found nothing.
 *
 * After this change: one shared bean injected into both.
 * Ingest once → both RagService and ResearcherAgent can search it.
 * This is Spring's dependency injection solving a real data sharing problem.
 */

/**
 * EmbeddingStoreConfig — shared EmbeddingStore bean with automatic bootstrap ingestion.
 * Keeps RagService completely untouched.
 */
@Slf4j
@Configuration
public class EmbeddingStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(ResourceLoader resourceLoader) {
        // 1. Initialize the shared in-memory database instance
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        log.info("[EmbeddingStore] Checking for knowledge.txt to auto-ingest at startup...");
        try {
            Resource resource = resourceLoader.getResource("classpath:knowledge.txt");
            if (resource.exists()) {
                String text = resource.getContentAsString(StandardCharsets.UTF_8);

                // 2. Wrap text into a LangChain4j document object
                Document document = Document.from(text,
                        dev.langchain4j.data.document.Metadata.from("source", "knowledge.txt")
                );

                // 3. Chunk the file structure (using your exact 300 token strategy)
                DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);
                List<TextSegment> segments = splitter.split(document);
                log.info("[EmbeddingStore] knowledge.txt split into {} chunks.", segments.size());

                // 4. Instantiating the local vector engine to map positions
                EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

                // 5. Build, index, and load records into the memory state
                segments.forEach(segment -> {
                    Embedding embedding = embeddingModel.embed(segment).content();
                    store.add(embedding, segment);
                });

                log.info("[EmbeddingStore] Successfully indexed and cached {} vectors into shared store.", segments.size());
            } else {
                log.warn("[EmbeddingStore] knowledge.txt was not found in classpath. Store initialized empty.");
            }
        } catch (Exception e) {
            log.error("❌ [EmbeddingStore] Failed to complete auto-ingestion at bean creation phase: ", e);
        }

        // Return the fully populated store instance to Spring's context
        return store;
    }
}
