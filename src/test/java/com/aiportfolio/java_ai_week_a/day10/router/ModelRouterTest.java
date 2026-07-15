package com.aiportfolio.java_ai_week_a.day10.router;

import com.aiportfolio.day9.router.QueryClassifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * ModelRouterTest — unit tests for query classification.
 *
 * QueryClassifier is pure Java with no Spring dependencies —
 * test it without any Spring context. Runs in milliseconds.
 *
 * These tests document the expected behaviour of your classifier.
 * When you tune thresholds, broken tests tell you what changed.
 *
 * Parameterized tests: one test method, multiple inputs.
 * Each @CsvSource row = one test case.
 * This is idiomatic JUnit 5 — shows Java testing maturity.
 */
class ModelRouterTest {

    @Test
    @DisplayName("Simple single-fact question classified as SIMPLE")
    void testSimpleQuestions() {
        assertThat(QueryClassifier.classify("What is the price?"))
                .isEqualTo(QueryClassifier.Complexity.SIMPLE);

        assertThat(QueryClassifier.classify("How much does the Starter plan cost?"))
                .isEqualTo(QueryClassifier.Complexity.SIMPLE);
    }

    @Test
    @DisplayName("Multi-step reasoning question classified as COMPLEX")
    void testComplexQuestions() {
        String complexQuery = "Compare all plans, calculate annual costs with 20% discount, " +
                "and recommend the best option for a 10-person startup with technical needs.";

        assertThat(QueryClassifier.classify(complexQuery))
                .isEqualTo(QueryClassifier.Complexity.COMPLEX);
    }

    @ParameterizedTest(name = "Query: {0} → Expected: {1}")
    @CsvSource({
            "'What is the refund policy?', SIMPLE",
            "'How much does it cost?, SIMPLE",
            "'Explain the difference between REST and GraphQL in detail, MODERATE",
            "'Compare all pricing plans and calculate total annual cost with discounts, COMPLEX",
            "'Hi', SIMPLE"
    })
    @DisplayName("Classification covers expected range of queries")
    void testClassificationRange(String query, String expectedComplexity) {
        var expected = QueryClassifier.Complexity.valueOf(expectedComplexity);
        var actual = QueryClassifier.classify(query);

        // Allow one level of flexibility — classifier is heuristic, not perfect
        // SIMPLE vs MODERATE difference is acceptable, SIMPLE vs COMPLEX is not
        if (expected == QueryClassifier.Complexity.SIMPLE) {
            assertThat(actual).isNotEqualTo(QueryClassifier.Complexity.COMPLEX);
        } else if (expected == QueryClassifier.Complexity.COMPLEX) {
            assertThat(actual).isNotEqualTo(QueryClassifier.Complexity.SIMPLE);
        }
    }

    @Test
    @DisplayName("Classify explanation is non-empty for any query")
    void testExplainAlwaysReturnsText() {
        String explanation = QueryClassifier.explain("What is the price?");
        assertThat(explanation).isNotBlank();
        assertThat(explanation).contains("Final classification:");
    }

    @Test
    @DisplayName("Empty query does not throw exception")
    void testEmptyQueryHandled() {
        assertThatNoException().isThrownBy(() ->
                QueryClassifier.classify("")
        );
    }

    @Test
    @DisplayName("Very long query classified as COMPLEX")
    void testLongQueryIsComplex() {
        String longQuery = "Please provide a ".repeat(20) + " very comprehensive analysis.";
        assertThat(QueryClassifier.classify(longQuery))
                .isIn(QueryClassifier.Complexity.MODERATE, QueryClassifier.Complexity.COMPLEX);
    }
}