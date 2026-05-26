package com.aiportfolio.Day2.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.util.List;


/**
 * DocumentIngestionService — handles the INGESTION half of RAG.
 *
 * Ingestion pipeline (runs once per document):
 *   Raw text → DocumentReader → List<Document>
 *            → TokenTextSplitter → List<Document> (chunks)
 *            → VectorStore.add() → embedded + stored
 *
 * Python equivalent (your Week 1 Day 3):
 *   DOCUMENT string → chunk_text() → List[str]
 *                  → get_embedding() for each chunk
 *                  → VectorStore.add_chunks()
 *
 * Key Spring AI advantage: DocumentReader abstracts the source.
 * Same pipeline handles text files, PDFs, web pages —
 * just swap the reader. Zero pipeline code changes.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;


    /**
     * TokenTextSplitter configuration.
     * defaultChunkSize=800: target tokens per chunk (not characters — tokens)
     * minChunkSizeChars=350: minimum chars before creating a chunk
     * minChunkLengthToEmbed=5: skip chunks shorter than this
     * maxNumChunks=10000: safety cap
     * keepSeparator=true: preserve sentence boundaries
     *
     * Compare to Python: RecursiveCharacterTextSplitter(chunk_size=300)
     * Difference: Spring AI splits by TOKEN count, Python split by CHARACTER count.
     * Token-based splitting is more accurate for LLM context windows.
     */

    private static final TokenTextSplitter SPLITTER = new TokenTextSplitter(
            800,    // defaultChunkSize
            350,    // minChunkSizeChars
            5,      // minChunkLengthToEmbed
            10000,  // maxNumChunks
            true    // keepSeparator
    );

    /**
     * Ingest raw text string.
     * Use when you have text content directly (not from a file).
     */

    public int ingestText(String text, String docName) {
        log.info("[Ingest] Processing text document: {}", docName);

        // Wrap text in a Spring Resource for TextReader
        var resource = new ByteArrayResource(text.getBytes()) {
            @Override
            public String getFilename() {
                return docName;
            }
        };

        var reader = new TextReader(resource);
        List<Document> rawDocs = reader.get();

        // Add metadata to all documents
        rawDocs.forEach(doc ->
                doc.getMetadata().put("source", docName)
                );

        // Split into chunks
        List<Document> chunks = SPLITTER.apply(rawDocs);
        log.info("[Ingest] Created {} chunks from '{}'", chunks.size(), docName);

        // Embed and store — VectorStore calls EmbeddingModel internally
        vectorStore.add(chunks);
        log.info("[Ingest] Stored {} chunks in vector store", chunks.size());

        return chunks.size();
    }

    /**
     * Ingest from file path.
     * TextReader reads the file, creates one Document per file.
     */

    public int ingestFile(String filePath) {
        log.info("[Ingest] Reading file: {}", filePath);

        var resource = new FileSystemResource(filePath);
        var reader = new TextReader(resource);
        List<Document> rawDocs = reader.get();

        rawDocs.forEach(doc ->
                doc.getMetadata().put("source", filePath)
        );

        List<Document> chunks = SPLITTER.apply(rawDocs);
        vectorStore.add(chunks);

        log.info("[Ingest] File ingested: {} chunks from {}", chunks.size(), filePath);
        return chunks.size();
    }

    /**
     * Ingest a list of documents directly.
     * Use when you have pre-built Document objects with custom metadata.
     *
     * This is the most flexible option — build Documents with any metadata
     * you need for filtering later.
     */

    public int ingestDocuments(List<Document> documents) {
        List<Document> chunks = SPLITTER.apply(documents);
        vectorStore.add(chunks);
        log.info("[Ingest] Ingested {} documents → {} chunks", documents.size(), chunks.size());
        return chunks.size();
    }

    /**
     * Ingest multiple categorised documents.
     * Each document gets a 'category' metadata field for filtered retrieval.
     *
     * Python equivalent: adding metadata to chunks in Day 3
     * doc.getMetadata() in Java = chunk["metadata"] in Python
     */

    public int ingestWithCategory(String text, String docName, String category) {

        var resource = new ByteArrayResource(text.getBytes()) {
            @Override
            public String getFilename() {
                return docName;
            }
        };

        var reader = new TextReader(resource);
        List<Document> rawDocs = reader.get();

        // Add both source and category metadata
        rawDocs.forEach(doc -> {
            doc.getMetadata().put("source", docName);
            doc.getMetadata().put("category", category);
        });

        List<Document> chunks = SPLITTER.apply(rawDocs);
        vectorStore.add(chunks);

        log.info("[Ingest] Categorised ingest: {} chunks, category={}", chunks.size(), category);
        return chunks.size();
    }
}
