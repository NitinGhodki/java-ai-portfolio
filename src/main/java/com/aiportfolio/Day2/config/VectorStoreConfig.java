package com.aiportfolio.Day2.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

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
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {

        var store = SimpleVectorStore.builder(embeddingModel).build();

        File persistFile = new File("vector-store.json");
        if (persistFile.exists()) {
            store.load(persistFile);
            System.out.println("[VectorStore] Loaded existing data from vector-store.json");
        }

        return store;
    }

}
