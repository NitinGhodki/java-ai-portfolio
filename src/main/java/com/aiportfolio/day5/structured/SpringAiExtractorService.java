package com.aiportfolio.day5.structured;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

/**
 * Spring AI structured extraction.
 *
 * Spring AI approach is more explicit:
 * 1. Create BeanOutputConverter for your target class
 * 2. Get the format instructions (JSON schema as a string)
 * 3. Add format instructions to your prompt manually
 * 4. Call LLM
 * 5. Call converter.convert(response) to deserialise
 *
 * More code than LangChain4j. More control over the prompt.
 * You can see and customise exactly what instructions go to the LLM.
 *
 * When to prefer Spring AI extraction over LangChain4j:
 * - When you need to customise the extraction prompt heavily
 * - When you need to debug why extraction is failing
 * - When you are already in a Spring AI codebase
 *
 * When to prefer LangChain4j:
 * - When you want minimal code
 * - When extraction is straightforward
 * - When automatic retry on failure is sufficient
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpringAiExtractorService {

    private final ChatModel chatModel;

    /**
     * Extract a Person from unstructured text.
     * Shows the full Spring AI extraction flow explicitly.
     */
    public ExtractionModels.Person extractPerson(String text) {
        log.info("[Spring AI Extract] Person from text");

        // Step 1: Create converter for target class
        var converter = new BeanOutputConverter<>(ExtractionModels.Person.class);

        // Step 2: Get format instructions — this is the JSON schema
        // as a natural language instruction for the LLM
        String formatInstructions = converter.getFormat();
        log.debug("[Spring AI Extract] Format instructions: {}",
                formatInstructions.substring(0, Math.min(100, formatInstructions.length())));

        // Step 3: Build prompt with format instructions appended
        String prompt = String.format("""
                Extract person information from the text below.
                
                Text: %s
                
                %s
                """, text, formatInstructions);

        // Step 4: Call LLM — returns raw string
        String rawResponse = ChatClient.create(chatModel)
                .prompt(prompt)
                .call()
                .content();

        log.debug("[Spring AI Extract] Raw response: {}",
                rawResponse.substring(0, Math.min(100, rawResponse.length())));

        // Step 5: Convert raw string to typed Java object
        ExtractionModels.Person result = converter.convert(rawResponse);
        log.info("[Spring AI Extract] Person: name={}, age={}",
                result.name(), result.age());

        return result;
    }

    public ExtractionModels.JobPosting extractJobPosting(String text) {
        var converter = new BeanOutputConverter<>(ExtractionModels.JobPosting.class);
        String prompt = String.format(
                "Extract job posting details from this text:\n%s\n\n%s",
                text, converter.getFormat()
        );
        String response = ChatClient.create(chatModel)
                .prompt(prompt).call().content();
        return converter.convert(response);
    }

    public ExtractionModels.SupportTicket classifyTicket(String text) {
        var converter = new BeanOutputConverter<>(ExtractionModels.SupportTicket.class);
        String prompt = String.format(
                "Classify this support ticket:\n%s\n\n%s",
                text, converter.getFormat()
        );
        String response = ChatClient.create(chatModel)
                .prompt(prompt).call().content();
        return converter.convert(response);
    }
}
