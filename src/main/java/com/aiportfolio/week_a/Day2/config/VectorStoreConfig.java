package com.aiportfolio.week_a.Day2.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.util.List;

/**
 * VectorStoreConfig — configures the vector store as a Spring bean.
 *
 * Why a @Configuration class?
 * VectorStore needs an EmbeddingModel injected.
 * Spring DI handles this automatically when you declare it as a @Bean.
 * Any class that needs the VectorStore just declares it as a parameter —
 * Spring injects the same instance everywhere. One store, many users.
 *
 * SimpleVectorStore:
 *   - In-memory storage (fast, no external DB needed)
 *   - File persistence (data survives restart)
 *   - Cosine similarity search built in
 *   - Good for: development, small datasets (<100k chunks)
 *   - Swap for PgVectorStore in production without changing code
 */

@Configuration
public class VectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel, ResourceLoader resourceLoader) {
        // Initialize a clean, empty in-memory store
        var store = SimpleVectorStore.builder(embeddingModel).build();

        System.out.println("[Spring AI] Ingesting knowledge.txt data into memory-only Vector Store...");
        try {
            var resource = resourceLoader.getResource("classpath:knowledge.txt");
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            List<Document> documents = reader.read();

            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocs = splitter.apply(documents);

            // Load the embedded text split vectors directly into application RAM
            store.accept(splitDocs);
            System.out.println("[Spring AI] Successfully loaded " + splitDocs.size() + " documents into memory!");
        } catch (Exception e) {
            System.err.println("❌ Failed to ingest data into in-memory store: " + e.getMessage());
            e.printStackTrace(); // Added stack trace to make future log debugging easier
        }

        return store;
    }

}
