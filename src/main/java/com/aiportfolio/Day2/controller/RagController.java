package com.aiportfolio.Day2.controller;

import com.aiportfolio.Day2.rag.DocumentIngestionService;
import com.aiportfolio.Day2.rag.RagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * RagController — REST API for the RAG pipeline.
 *
 * Endpoints:
 * POST /api/rag/ingest/text      — ingest raw text
 * POST /api/rag/ingest/file      — upload and ingest a file
 * POST /api/rag/query            — query (advisor mode)
 * POST /api/rag/query/manual     — query (manual mode — shows retrieved chunks)
 * POST /api/rag/query/filtered   — query with category filter
 */

@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagPipeline ragPipeline;
    private final DocumentIngestionService ingestionService;

    // Request records

    public record IngestTextRequest(String text, String docName) {}
    public record QueryRequest(String question, int topK) {
        public QueryRequest { if (topK <= 0) topK = 3; }
    }
    public record FilteredQueryRequest(String question, String category, int topK) {
        public FilteredQueryRequest { if (topK <= 0) topK = 3; }
    }
    public record IngestResponse(String docName, int chunksCreated, String status) {}

    // Endpoints

    /**
     * POST /api/rag/ingest/text
     *
     * curl -X POST http://localhost:8080/api/rag/ingest/text \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "The Starter plan costs 999 rupees per month.", "docName": "pricing.txt"}'
     */
    @PostMapping("/ingest/text")
    public ResponseEntity<IngestResponse> ingestText(@RequestBody IngestTextRequest request) {
        int chunks = ingestionService.ingestText(request.text(), request.docName());
        return ResponseEntity.ok(new IngestResponse(request.docName(), chunks, "ingested"));
    }

    /**
     * POST /api/rag/ingest/file
     * Multipart file upload.
     *
     * curl -X POST http://localhost:8080/api/rag/ingest/file \
     *   -F "file=@knowledge_base.txt"
     */
    @PostMapping("/ingest/file")
    public ResponseEntity<IngestResponse> ingestFile(@RequestParam("file") MultipartFile file)
            throws IOException {

        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        String docName = file.getOriginalFilename();
        int chunks = ingestionService.ingestText(text, docName);

        return ResponseEntity.ok(new IngestResponse(docName, chunks, "ingested"));
    }

    /**
     * POST /api/rag/query
     * Uses QuestionAnswerAdvisor — simple, Spring AI handles everything.
     *
     * curl -X POST http://localhost:8080/api/rag/query \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What is the refund policy?", "topK": 3}'
     */
    @PostMapping("/query")
    public ResponseEntity<RagPipeline.RagResponse> query(@RequestBody QueryRequest request) {
        var response = ragPipeline.queryWithAdvisor(request.question(), request.topK());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/rag/query/manual
     * Full control — you see exactly which chunks were retrieved.
     *
     * curl -X POST http://localhost:8080/api/rag/query/manual \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "How much does the Professional plan cost?", "topK": 3}'
     */
    @PostMapping("/query/manual")
    public ResponseEntity<RagPipeline.RagResponse> queryManual(@RequestBody QueryRequest request) {
        var response = ragPipeline.queryManual(request.question(), request.topK());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/rag/query/filtered
     * Search only within a specific document category.
     *
     * curl -X POST http://localhost:8080/api/rag/query/filtered \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What is the price?", "category": "pricing", "topK": 3}'
     */
    @PostMapping("/query/filtered")
    public ResponseEntity<RagPipeline.RagResponse> queryFiltered(
            @RequestBody FilteredQueryRequest request) {
        var response = ragPipeline.queryWithFilter(
                request.question(), request.category(), request.topK()
        );
        return ResponseEntity.ok(response);
    }
}
