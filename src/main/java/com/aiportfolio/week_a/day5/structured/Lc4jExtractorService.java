package com.aiportfolio.week_a.day5.structured;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * LangChain4j structured extraction.
 *
 * The core insight: return type IS the extraction schema.
 * You declare what you want. LangChain4j handles everything else.
 *
 * Internal flow:
 * 1. AiServices inspects the return type (e.g. Person.class)
 * 2. Generates JSON schema from the record's fields and descriptions
 * 3. Adds schema to the prompt automatically
 * 4. Receives JSON from LLM
 * 5. Deserializes into the Java record
 * 6. Returns typed object — you never see JSON
 *
 * Retry on parse failure: LangChain4j retries automatically (default 3x).
 * You write zero retry logic. Compare to Python Day 2's retry loop.
 *
 * Python equivalent:
 *   structured_extractor.py — your StructuredExtractor class
 *   with _build_extraction_prompt(), json.loads(), ValidationError retry
 *   All of that = this one interface declaration.
 */
@Slf4j
@Service
public class Lc4jExtractorService {

    /**
     * Extractor interface — one method per extraction type.
     * Return type determines what gets extracted.
     * @UserMessage marks which parameter is the input text.
     */
    @SystemMessage("""
            You are a precise data extraction assistant.
            Extract information exactly as stated in the text.
            Do not infer or add information not explicitly present.
            For missing optional fields, use null.
            """)
    interface Extractor {
        ExtractionModels.Person extractPerson(@UserMessage String text);
        ExtractionModels.JobPosting extractJobPosting(@UserMessage String text);
        ExtractionModels.SupportTicket classifyTicket(@UserMessage String text);
        ExtractionModels.ProductReview analyzeReview(@UserMessage String text);
    }

    private final Extractor extractor;

    public Lc4jExtractorService(ChatModel model) {
        this.extractor = AiServices.create(Extractor.class, model);
    }

    public ExtractionModels.Person extractPerson(String text) {
        log.info("[LC4J Extract] Person from: {}...", text.substring(0, Math.min(50, text.length())));
        ExtractionModels.Person result = extractor.extractPerson(text);
        log.info("[LC4J Extract] Person: name={}, age={}, skills={}",
                result.name(), result.age(), result.skills());
        return result;
    }

    public ExtractionModels.JobPosting extractJobPosting(String text) {
        log.info("[LC4J Extract] JobPosting from text");
        return extractor.extractJobPosting(text);
    }

    public ExtractionModels.SupportTicket classifyTicket(String text) {
        log.info("[LC4J Extract] SupportTicket classification");
        return extractor.classifyTicket(text);
    }

    public ExtractionModels.ProductReview analyzeReview(String text) {
        log.info("[LC4J Extract] ProductReview analysis");
        return extractor.analyzeReview(text);
    }
}
