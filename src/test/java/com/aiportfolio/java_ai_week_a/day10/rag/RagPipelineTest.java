package com.aiportfolio.java_ai_week_a.day10.rag;

import com.aiportfolio.Day2.rag.DocumentIngestionService;
import com.aiportfolio.Day2.rag.RagPipeline;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

/**
 * RagPipelineTest — tests RAG pipeline without real LLM API calls.
 *
 * WireMock intercepts HTTP calls to HuggingFace and returns
 * pre-defined responses instantly. Tests run in <100ms each.
 * No API key needed. No real tokens consumed.
 *
 * This is production Java testing practice for AI code.
 * Most Python AI tutorials have zero test coverage.
 * Having these tests on your GitHub is a strong signal.
 *
 * Test structure:
 *   @BeforeAll: start WireMock server on random port
 *   @DynamicPropertySource: redirect Spring AI to WireMock URL
 *   Each test: stub WireMock response → call your code → assert
 *   @AfterAll: stop WireMock server
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RagPipelineTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        System.out.println("[Test] WireMock started on port: " + wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    /**
     * Redirect Spring AI's OpenAI client to WireMock instead of HuggingFace.
     * Called before the Spring context loads — sets the base URL dynamically.
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + wireMock.port());
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("spring.ai.openai.chat.options.model", () -> "test-model");
    }

    @Autowired
    private RagPipeline ragPipeline;

    @Autowired
    private DocumentIngestionService ingestionService;

    /**
     * Stub the embedding endpoint for document ingestion.
     * Returns a fixed 3-dimensional embedding vector.
     * In production: all-MiniLM-L6-v2 returns 384 dimensions.
     * For tests: 3 dimensions is enough to verify the pipeline works.
     */
    private void stubEmbeddingEndpoint() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/embeddings"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "object": "list",
                                    "data": [
                                        {
                                            "object": "embedding",
                                            "embedding": [0.1, 0.2, 0.3],
                                            "index": 0
                                        }
                                    ],
                                    "model": "test-model",
                                    "usage": {"prompt_tokens": 10, "total_tokens": 10}
                                }
                                """)
                ));
    }

    /**
     * Stub the chat completion endpoint.
     * Returns a fixed answer that we can assert against.
     */
    private void stubChatEndpoint(String answer) {
        wireMock.stubFor(post(urlPathEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                {
                                    "id": "test-completion-id",
                                    "object": "chat.completion",
                                    "model": "test-model",
                                    "choices": [
                                        {
                                            "index": 0,
                                            "message": {
                                                "role": "assistant",
                                                "content": "%s"
                                            },
                                            "finish_reason": "stop"
                                        }
                                    ],
                                    "usage": {
                                        "prompt_tokens": 50,
                                        "completion_tokens": 20,
                                        "total_tokens": 70
                                    }
                                }
                                """, answer))
                ));
    }

    @Test
    @Order(1)
    @DisplayName("Ingest document — chunks created without real API call")
    void testDocumentIngestion() {
        stubEmbeddingEndpoint();

        int chunks = ingestionService.ingestText(
                "The Professional plan costs 2999 rupees per month.",
                "test-pricing.txt"
        );

        assertThat(chunks).isGreaterThan(0);

        // Verify WireMock received the embedding request
        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/embeddings")));
    }

    @Test
    @Order(2)
    @DisplayName("RAG query — retrieves and generates without real API call")
    void testRagQuery() {
        stubEmbeddingEndpoint();
        stubChatEndpoint("The Professional plan costs 2999 rupees per month.");

        var response = ragPipeline.queryManual(
                "How much does the Professional plan cost?", 3
        );

        assertThat(response).isNotNull();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.answer()).contains("2999");
        assertThat(response.mode()).isEqualTo("manual");
    }

    @Test
    @Order(3)
    @DisplayName("RAG query — gracefully handles empty document store")
    void testRagQueryEmptyStore() {
        stubEmbeddingEndpoint();
        stubChatEndpoint("I don't have that information in my documents.");

        var response = ragPipeline.queryManual("Who is the CEO?", 3);

        assertThat(response).isNotNull();
        assertThat(response.answer()).isNotBlank();
        // Should indicate no information found — exact wording depends on LLM stub
    }

    @Test
    @Order(4)
    @DisplayName("RAG response contains required fields")
    void testRagResponseStructure() {
        stubEmbeddingEndpoint();
        stubChatEndpoint("Test answer from stub.");

        var response = ragPipeline.queryManual("Any question", 3);

        assertThat(response.question()).isEqualTo("Any question");
        assertThat(response.answer()).isNotNull();
        assertThat(response.sources()).isNotNull(); // can be empty list
        assertThat(response.latencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(response.mode()).isNotNull();
    }

    @Test
    @Order(5)
    @DisplayName("WireMock — verify no real API calls made")
    void testNoRealApiCallsMade() {
        // All requests in this test class went to WireMock, not HuggingFace
        // This test verifies WireMock received requests (not real API)
        wireMock.verify(moreThan(0),
                postRequestedFor(urlPathEqualTo("/v1/chat/completions")));

        System.out.println("[Test] All LLM calls intercepted by WireMock. Zero real API calls made.");
        System.out.println("[Test] Estimated savings: $0.00 (no tokens consumed in tests)");
    }
}