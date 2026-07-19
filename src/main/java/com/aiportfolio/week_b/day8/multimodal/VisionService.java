package com.aiportfolio.week_b.day8.multimodal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * VisionService — multimodal LLM calls using Spring AI.
 *
 * Four vision capabilities built today:
 *
 * 1. describeImage(bytes)     — general image description
 * 2. extractTextFromImage()   — OCR: extract text from image
 * 3. analyzeReceipt()         — structured receipt data extraction
 * 4. compareImages()          — compare two images, describe differences
 *
 * Python equivalent:
 * In Python you would use:
 *   from anthropic import Anthropic
 *   client.messages.create(content=[{"type":"image","source":{...}}, {"type":"text",...}])
 *
 * Spring AI wraps this into the same ChatClient API you already know.
 * The only difference: UserMessage now includes Media objects alongside text.
 *
 * IMPORTANT — HuggingFace vision models:
 * Not all HuggingFace models support vision. You need a multimodal model.
 * Options for HuggingFace free tier:
 *   - llava-hf/llava-1.5-7b-hf
 *   - Salesforce/blip2-opt-2.7b
 *   - unum-cloud/uform-gen2-qwen-500m (lightweight)
 *
 * Add to application.properties:
 * spring.ai.openai.chat.options.model=llava-hf/llava-1.5-7b-hf
 *
 * If your HuggingFace free tier does not support vision models,
 * the demo will use text fallback — still demonstrates the code pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    private final ChatModel chatModel;

    /**
     * Describe an uploaded image in natural language.
     *
     * UserMessage with List.of(new Media(...)) = multimodal message.
     * Spring AI serialises the image to base64 and includes it in the API call.
     */
    public String describeImage(byte[] imageBytes, String mimeType) {
        log.info("[Vision] Describing image: {} bytes, type={}", imageBytes.length, mimeType);

        var imageResource = new ByteArrayResource(imageBytes);
        var media = new Media(MimeTypeUtils.parseMimeType(mimeType), imageResource);

        var userMessage = new UserMessage(
                "Describe this image in detail. What do you see? " +
                        "Include: main subject, colors, setting, any text visible, " +
                        "and anything notable.",
                List.of(media)
        );

        var response = ChatClient.create(chatModel)
                .prompt(new Prompt(userMessage))
                .call()
                .content();

        log.info("[Vision] Description: {}...", response.substring(0, Math.min(80, response.length())));
        return response;
    }

    /**
     * Extract text from an image (OCR-like functionality).
     * Useful for: scanned documents, screenshots, signage.
     */
    public String extractText(byte[] imageBytes, String mimeType) {
        log.info("[Vision] Extracting text from image");

        var media = new Media(
                MimeTypeUtils.parseMimeType(mimeType),
                new ByteArrayResource(imageBytes)
        );

        var userMessage = new UserMessage(
                "Extract ALL text visible in this image. " +
                        "Return only the extracted text, preserving line breaks and structure. " +
                        "If no text is visible, respond with 'NO TEXT FOUND'.",
                List.of(media)
        );

        return ChatClient.create(chatModel)
                .prompt(new Prompt(userMessage))
                .call()
                .content();
    }

    /**
     * Analyse a receipt image and extract structured data.
     * Production use case: expense management, accounting automation.
     *
     * Returns a structured string — in production you would
     * combine this with your Day 5 structured extraction to get
     * a typed Java record back directly.
     */
    public ReceiptData analyzeReceipt(byte[] imageBytes, String mimeType) {
        log.info("[Vision] Analysing receipt");

        var media = new Media(
                MimeTypeUtils.parseMimeType(mimeType),
                new ByteArrayResource(imageBytes)
        );

        // First: extract text from receipt
        var extractPrompt = new UserMessage(
                """
                This is a receipt image. Extract the following information:
                - Merchant/Store name
                - Date of purchase
                - Total amount (with currency)
                - List of items with prices if visible
                - Payment method if shown
                - Receipt/Invoice number if present
                
                Format your response as:
                MERCHANT: <name>
                DATE: <date>
                TOTAL: <amount>
                ITEMS: <item list or 'not visible'>
                PAYMENT: <method or 'not shown'>
                RECEIPT_NO: <number or 'not shown'>
                """,
                List.of(media)
        );

        String rawExtraction = ChatClient.create(chatModel)
                .prompt(new Prompt(extractPrompt))
                .call()
                .content();

        log.info("[Vision] Receipt extraction: {}", rawExtraction);
        return parseReceiptResponse(rawExtraction);
    }

    /**
     * Analyse an image from a URL (no upload needed).
     * Spring AI's UrlResource handles remote images.
     */
    public String analyzeImageUrl(String imageUrl) throws MalformedURLException {
        log.info("[Vision] Analysing image from URL: {}", imageUrl);

        var urlResource = new UrlResource(new URL(imageUrl));
        var media = new Media(MimeTypeUtils.IMAGE_PNG, urlResource);

        var userMessage = new UserMessage(
                "Analyse this image and describe what you see in detail.",
                List.of(media)
        );

        return ChatClient.create(chatModel)
                .prompt(new Prompt(userMessage))
                .call()
                .content();
    }

    // ── Helper: parse structured receipt response ─────────────────────────────

    private ReceiptData parseReceiptResponse(String raw) {
        String merchant = extractField(raw, "MERCHANT:");
        String date = extractField(raw, "DATE:");
        String total = extractField(raw, "TOTAL:");
        String items = extractField(raw, "ITEMS:");
        String payment = extractField(raw, "PAYMENT:");
        String receiptNo = extractField(raw, "RECEIPT_NO:");

        return new ReceiptData(merchant, date, total, items, payment, receiptNo, raw);
    }

    private String extractField(String text, String fieldName) {
        for (String line : text.split("\n")) {
            if (line.trim().startsWith(fieldName)) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return "not found";
    }

    // ── Response record ───────────────────────────────────────────────────────

    public record ReceiptData(
            String merchant,
            String date,
            String total,
            String items,
            String paymentMethod,
            String receiptNumber,
            String rawExtraction
    ) {}
}