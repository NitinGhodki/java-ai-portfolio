package com.aiportfolio.week_b.day8.evaluation;

import com.aiportfolio.week_a.Day2.rag.RagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RagEvaluationService — Java equivalent of your Python Week 2 Day 10 RAGAS pipeline.
 *
 * What this does:
 * 1. Takes a set of test questions with ground truth answers
 * 2. Runs each through your RAG pipeline
 * 3. Evaluates each response with Spring AI's built-in evaluators
 * 4. Returns aggregated quality scores
 *
 * Python RAGAS had 4 metrics: faithfulness, answer_relevancy, context_recall,
 * context_precision.
 *
 * Spring AI built-in evaluators:
 * - RelevancyEvaluator: is the response relevant to the question?
 * - FaithfulnessEvaluator: is the response grounded in the retrieved context?
 *
 * Gap vs RAGAS: no context_recall or context_precision equivalent.
 * For full evaluation parity with Python: still use RAGAS (Python).
 * For quick development-time sanity checks: Spring AI evaluators are fine.
 * Know this distinction for interviews.
 *
 * How evaluators work internally:
 * They make a SECOND LLM call with a meta-prompt:
 *   "Given this question: X
 *    And this response: Y
 *    Is the response relevant? Answer YES or NO with a score 0-1."
 * This is LLM-as-judge pattern — same as Python RAGAS uses internally.
 * Same limitation: evaluator quality depends on evaluator LLM quality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvaluationService {

    private final ChatModel chatModel;
    private final RagPipeline ragPipeline;

    /**
     * Test dataset — same structure as your Python eval_dataset.json.
     * question: the test query
     * groundTruth: the correct answer (written by you manually)
     *
     * In production: load from a JSON file or database.
     * Writing ground truth manually is unavoidable — you cannot automate
     * the definition of "correct." Same lesson as Python Day 10.
     */
    public static final List<EvalSample> TEST_DATASET = List.of(
            new EvalSample(
                    "What does the Professional plan cost per month?",
                    "The Professional plan costs 2999 rupees per month.",
                    "pricing"
            ),
//            new EvalSample(
//                    "Can I get a refund on a monthly plan?",
//                    "Monthly plans can be cancelled anytime but are not eligible for partial refunds.",
//                    "refund"
//            ),
//            new EvalSample(
//                    "How long is the free trial?",
//                    "All plans come with a 14-day free trial with no credit card required.",
//                    "trial"
//            ),
//            new EvalSample(
//                    "What is the API rate limit for the Starter plan?",
//                    "The Starter plan has a rate limit of 10 requests per second.",
//                    "technical"
//            ),
            new EvalSample(
                    "What support response time do Enterprise customers get?",
                    "Enterprise customers get a dedicated support engineer with a 1-hour response SLA.",
                    "support"
            )
    );

    /**
     * Run full evaluation on the test dataset.
     * Returns aggregated scores + per-question breakdown.
     */
    public EvaluationReport evaluate(int topK) {
        log.info("[Eval] Starting evaluation on {} test samples", TEST_DATASET.size());

        var evaluator = new RelevancyEvaluator(ChatClient.builder(chatModel));
        List<QuestionResult> results = new ArrayList<>();

        for (EvalSample sample : TEST_DATASET) {
            log.info("[Eval] Evaluating: {}", sample.question());

            // Step 1: Get RAG response
            var ragResponse = ragPipeline.queryManual(sample.question(), topK);

            // Step 2: Build evaluation request
            // EvaluationRequest takes: question, retrieved context, LLM response
            String context = String.join("\n", ragResponse.sources());
            var evalRequest = new EvaluationRequest(
                    sample.question(),
                    List.of(Document.builder().text(context).build()),
                    ragResponse.answer()
            );

            // Step 3: Run relevancy evaluation
            EvaluationResponse relevancyResult;
            try {
                relevancyResult = evaluator.evaluate(evalRequest);
            } catch (Exception e) {
                log.error("[Eval] Evaluator failed for question: {}", sample.question(), e);
                relevancyResult = new EvaluationResponse(false, "Evaluation failed: " + e.getMessage(),
                        Map.of("Evaluation failed: " + e.getMessage(), ragResponse));
            }

            // Step 4: Manual faithfulness check
            // Spring AI's FaithfulnessEvaluator works similarly
            // We do a simple heuristic version here to show the concept
            boolean simpleGroundTruthMatch = ragResponse.answer()
                    .toLowerCase()
                    .contains(extractKeyword(sample.groundTruth()));

            var result = new QuestionResult(
                    sample.question(),
                    sample.groundTruth(),
                    ragResponse.answer(),
                    ragResponse.sources(),
                    relevancyResult.isPass(),
                    (double) relevancyResult.getScore(),
                    simpleGroundTruthMatch,
                    sample.category()
            );
            results.add(result);

            log.info("[Eval] Question: {} | Relevant: {} | Score: {}",
                    sample.question().substring(0, 30),
                    relevancyResult.isPass(),
                    relevancyResult.getScore());
        }

        return buildReport(results);
    }

    private EvaluationReport buildReport(List<QuestionResult> results) {
        double avgRelevancyScore = results.stream()
                .mapToDouble(QuestionResult::relevancyScore)
                .average()
                .orElse(0.0);

        long relevantCount = results.stream()
                .filter(QuestionResult::isRelevant)
                .count();

        long groundTruthMatchCount = results.stream()
                .filter(QuestionResult::matchesGroundTruth)
                .count();

        // Group results by category
        Map<String, List<QuestionResult>> byCategory = results.stream()
                .collect(Collectors.groupingBy(QuestionResult::category));

        Map<String, Double> categoryScores = byCategory.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .mapToDouble(QuestionResult::relevancyScore)
                                .average()
                                .orElse(0.0)
                ));

        return new EvaluationReport(
                results.size(),
                (double) relevantCount / results.size(),
                avgRelevancyScore,
                (double) groundTruthMatchCount / results.size(),
                categoryScores,
                results,
                "Spring AI RelevancyEvaluator",
                buildVsRagasComparison()
        );
    }

    private String extractKeyword(String groundTruth) {
        // Extract first significant word from ground truth for simple matching
        String[] words = groundTruth.toLowerCase().split("\\s+");
        for (String word : words) {
            if (word.length() > 4 && !List.of("that", "this", "with", "from", "have")
                    .contains(word)) {
                return word;
            }
        }
        return words[0];
    }

    private String buildVsRagasComparison() {
        return """
                Comparison: Spring AI Evaluators vs Python RAGAS
                
                Spring AI (this evaluation):
                  Metrics: RelevancyEvaluator (pass/fail + score)
                  Coverage: answer relevancy, basic faithfulness
                  Integration: native Java, no Python dependency
                  Granularity: per-response pass/fail
                
                Python RAGAS (Week 2 Day 10):
                  Metrics: faithfulness, answer_relevancy, context_recall, context_precision
                  Coverage: all four dimensions of RAG quality
                  Integration: requires Python + external LLM for evaluation
                  Granularity: numeric scores 0-1, aggregated across dataset
                
                Recommendation: use Spring AI evaluators for quick dev-time checks.
                Use Python RAGAS for production evaluation pipelines and CI/CD gates.
                """;
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public record EvalSample(String question, String groundTruth, String category) {}

    public record QuestionResult(
            String question,
            String groundTruth,
            String ragAnswer,
            List<String> sources,
            boolean isRelevant,
            double relevancyScore,
            boolean matchesGroundTruth,
            String category
    ) {}

    public record EvaluationReport(
            int totalQuestions,
            double relevancyPassRate,
            double avgRelevancyScore,
            double groundTruthMatchRate,
            Map<String, Double> scoreByCategory,
            List<QuestionResult> questionResults,
            String evaluatorUsed,
            String vsRagasComparison
    ) {}
}