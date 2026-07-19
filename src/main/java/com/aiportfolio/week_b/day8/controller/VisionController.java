package com.aiportfolio.week_b.day8.controller;

import com.aiportfolio.week_b.day8.multimodal.VisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * VisionController — REST API for all vision capabilities.
 *
 * Endpoints:
 * POST /api/vision/describe   — describe uploaded image
 * POST /api/vision/ocr        — extract text from image
 * POST /api/vision/receipt    — analyse receipt image
 * POST /api/vision/url        — analyse image from URL
 */
@Slf4j
@RestController
@RequestMapping("/api/vision")
@RequiredArgsConstructor
public class VisionController {

    private final VisionService visionService;

    /**
     * POST /api/vision/describe
     * Upload an image file, get a natural language description.
     *
     * curl -X POST http://localhost:8080/api/vision/describe \
     *   -F "image=@/path/to/your/image.png"
     */
    @PostMapping("/describe")
    public ResponseEntity<?> describe(@RequestParam("image") MultipartFile image) {
        try {
            String description = visionService.describeImage(
                    image.getBytes(),
                    image.getContentType()
            );
            return ResponseEntity.ok(Map.of(
                    "filename", image.getOriginalFilename(),
                    "sizeBytes", image.getSize(),
                    "description", description
            ));
        } catch (Exception e) {
            log.error("Vision describe failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/vision/ocr
     * Extract text from an image.
     *
     * curl -X POST http://localhost:8080/api/vision/ocr \
     *   -F "image=@/path/to/document.png"
     */
    @PostMapping("/ocr")
    public ResponseEntity<?> extractText(@RequestParam("image") MultipartFile image) {
        try {
            String text = visionService.extractText(
                    image.getBytes(),
                    image.getContentType()
            );
            return ResponseEntity.ok(Map.of(
                    "filename", image.getOriginalFilename(),
                    "extractedText", text,
                    "wordCount", text.split("\\s+").length
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/vision/receipt
     * Analyse a receipt image and extract structured data.
     *
     * curl -X POST http://localhost:8080/api/vision/receipt \
     *   -F "image=@/path/to/receipt.jpg"
     */
    @PostMapping("/receipt")
    public ResponseEntity<?> analyzeReceipt(@RequestParam("image") MultipartFile image) {
        try {
            var receiptData = visionService.analyzeReceipt(
                    image.getBytes(),
                    image.getContentType()
            );
            return ResponseEntity.ok(receiptData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/vision/url
     * Analyse an image from a public URL — no upload needed.
     *
     * curl -X POST http://localhost:8080/api/vision/url \
     *   -H "Content-Type: application/json" \
     *   -d '{"url": "https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/240px-PNG_transparency_demonstration_1.png"}'
     */
    @PostMapping("/url")
    public ResponseEntity<?> analyzeUrl(@RequestBody Map<String, String> req) {
        try {
            String description = visionService.analyzeImageUrl(req.get("url"));
            return ResponseEntity.ok(Map.of(
                    "url", req.get("url"),
                    "description", description
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
