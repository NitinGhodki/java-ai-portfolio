package com.aiportfolio.week_a.day5.controller;

import com.aiportfolio.week_a.day5.structured.Lc4jExtractorService;
import com.aiportfolio.week_a.day5.structured.SpringAiExtractorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * ExtractionController — runs both extractors on the same input.
 * Lets you compare LangChain4j vs Spring AI results side by side.
 */
@Slf4j
@RestController
@RequestMapping("/api/extract")
@RequiredArgsConstructor
public class ExtractionController {

    private final Lc4jExtractorService lc4j;
    private final SpringAiExtractorService springAi;

    public record TextRequest(String text) {}

    /**
     * POST /api/extract/person
     * Runs BOTH extractors on the same text. Returns both results.
     *
     * curl -X POST http://localhost:8080/api/extract/person \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "Meet Nitin Ghodki, a 25-year-old backend developer from Chhindwara. He has 2.5 years of Java experience and is now learning Python, LangChain, and Spring AI."}'
     */
    @PostMapping("/person")
    public ResponseEntity<?> extractPerson(@RequestBody TextRequest req) {
        long start = Instant.now().toEpochMilli();
        var lc4jResult = lc4j.extractPerson(req.text());
        long lc4jMs = Instant.now().toEpochMilli() - start;

        start = Instant.now().toEpochMilli();
        var springResult = springAi.extractPerson(req.text());
        long springMs = Instant.now().toEpochMilli() - start;

        return ResponseEntity.ok(Map.of(
                "input", req.text(),
                "langchain4j", Map.of("result", lc4jResult, "latencyMs", lc4jMs),
                "springAi", Map.of("result", springResult, "latencyMs", springMs)
        ));
    }

    /**
     * POST /api/extract/job
     *
     * curl -X POST http://localhost:8080/api/extract/job \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "We are hiring a Senior AI Engineer at DataFlow India, Bengaluru. Minimum 3 years experience with Python, LangChain, and vector databases required. Salary: 25-40 LPA."}'
     */
    @PostMapping("/job")
    public ResponseEntity<?> extractJob(@RequestBody TextRequest req) {
        var lc4jResult = lc4j.extractJobPosting(req.text());
        var springResult = springAi.extractJobPosting(req.text());
        return ResponseEntity.ok(Map.of(
                "langchain4j", lc4jResult,
                "springAi", springResult
        ));
    }

    /**
     * POST /api/extract/ticket
     *
     * curl -X POST http://localhost:8080/api/extract/ticket \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "I have been charged TWICE this month and nobody is helping me! I sent 3 emails. This is completely unacceptable. I want a refund IMMEDIATELY."}'
     */
    @PostMapping("/ticket")
    public ResponseEntity<?> classifyTicket(@RequestBody TextRequest req) {
        var lc4jResult = lc4j.classifyTicket(req.text());
        var springResult = springAi.classifyTicket(req.text());
        return ResponseEntity.ok(Map.of(
                "langchain4j", lc4jResult,
                "springAi", springResult
        ));
    }

    /**
     * POST /api/extract/review
     * LangChain4j only — Spring AI version is your exercise.
     *
     * curl -X POST http://localhost:8080/api/extract/review \
     *   -H "Content-Type: application/json" \
     *   -d '{"text": "The Spring AI framework is very well documented and integrates perfectly with existing Spring Boot projects. The auto-configuration saves hours of setup. However, the RAG features are less mature than LangChain4j and structured output requires more boilerplate. Overall I would recommend it for teams already on Spring Boot. 4 out of 5 stars."}'
     */
    @PostMapping("/review")
    public ResponseEntity<?> analyzeReview(@RequestBody TextRequest req) {
        var result = lc4j.analyzeReview(req.text());
        return ResponseEntity.ok(result);
    }
}
