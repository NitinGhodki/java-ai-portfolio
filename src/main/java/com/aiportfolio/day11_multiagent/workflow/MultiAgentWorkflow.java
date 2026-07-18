package com.aiportfolio.day11_multiagent.workflow;

import com.aiportfolio.day11_multiagent.agents.CriticAgent;
import com.aiportfolio.day11_multiagent.agents.ResearcherAgent;
import com.aiportfolio.day11_multiagent.agents.WriterAgent;
import com.aiportfolio.day11_multiagent.state.NextAgent;
import com.aiportfolio.day11_multiagent.state.WorkflowState;
import com.aiportfolio.day11_multiagent.supervisor.Supervisor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MultiAgentWorkflow — orchestrates all agents through the Supervisor.
 *
 * Two execution modes:
 *
 * Mode 1: Sequential (simple, always correct)
 *   Supervisor → Researcher → Supervisor → Writer → Supervisor → Critic → Supervisor → END
 *   Each agent runs after the previous completes.
 *   Total time = sum of all agent latencies.
 *
 * Mode 2: Parallel where possible (faster, more complex)
 *   Researcher and context gathering run simultaneously.
 *   Writer runs after Researcher (data dependency).
 *   Critic verifies while next Writer revision is being prepared.
 *   Total time < sum of all agent latencies.
 *
 * Python Week 2 equivalent ran sequentially.
 * Today you implement both and measure the difference.
 *
 * CompletableFuture is Java's equivalent of Python asyncio.
 * CompletableFuture.supplyAsync() → asyncio.create_task()
 * CompletableFuture.allOf()       → asyncio.gather()
 * .thenApply()                    → .then() chaining
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentWorkflow {

    private final ResearcherAgent researcher;
    private final WriterAgent writer;
    private final CriticAgent critic;
    private final Supervisor supervisor;

    // Thread pool for parallel agent execution
    private final ExecutorService executorService =
            Executors.newFixedThreadPool(4);

    // ── Mode 1: Sequential execution ─────────────────────────────────────────

    /**
     * Run the full workflow sequentially.
     * Supervisor decides next agent after each step.
     * Maximum 10 iterations to prevent infinite loops.
     */
    public WorkflowResult runSequential(WorkflowState initialState) {
        log.info("[Workflow Sequential] Starting for session: {}", initialState.getSessionId());
        long start = Instant.now().toEpochMilli();

        WorkflowState state = initialState;
        int maxIterations = 10;
        int iteration = 0;

        while (iteration++ < maxIterations) {
            NextAgent nextAgent = supervisor.decide(state);

            state = switch (nextAgent) {
                case RESEARCHER -> {
                    log.info("[Workflow] → Researcher (iteration {})", iteration);
                    yield researcher.execute(state);
                }
                case WRITER -> {
                    log.info("[Workflow] → Writer (iteration {})", iteration);
                    yield writer.execute(state);
                }
                case CRITIC -> {
                    log.info("[Workflow] → Critic (iteration {})", iteration);
                    yield critic.execute(state);
                }
                case END -> {
                    log.info("[Workflow] → END after {} iterations", iteration);
                    yield state;
                }
            };

            if (nextAgent == NextAgent.END) break;
        }

        long totalMs = Instant.now().toEpochMilli() - start;
        String finalOutput = state.getFinalOutput().isBlank()
                ? state.getWrittenDraft()
                : state.getFinalOutput();

        log.info("[Workflow Sequential] Complete in {}ms", totalMs);
        return new WorkflowResult(finalOutput, state.getExecutionLog(), totalMs, "sequential");
    }

    // ── Mode 2: Parallel execution where possible ─────────────────────────────

    /**
     * Run with parallel execution where data dependencies allow.
     *
     * Dependency analysis:
     *   Researcher has no dependencies → can start immediately
     *   Writer depends on Researcher output → must wait
     *   Critic depends on Writer output → must wait
     *   Second Writer revision can start preparing while Critic is reviewing first draft
     *
     * Current parallel opportunity:
     *   If we need to run the full pipeline twice (original + one revision),
     *   the second Researcher call (if any) can overlap with the first Critic call.
     *
     * For the base case (one pass through Researcher → Writer → Critic → END),
     * there is no parallelism opportunity — each step depends on the previous.
     *
     * The real parallel opportunity: running MULTIPLE independent queries
     * simultaneously. This is demonstrated in the batch endpoint below.
     */
    public CompletableFuture<WorkflowResult> runAsync(WorkflowState initialState) {
        log.info("[Workflow Async] Starting async execution for session: {}",
                initialState.getSessionId());
        long start = Instant.now().toEpochMilli();

        // Start researcher asynchronously
        CompletableFuture<WorkflowState> researchFuture = CompletableFuture
                .supplyAsync(() -> {
                    log.info("[Workflow Async] Researcher starting on thread: {}",
                            Thread.currentThread().getName());
                    return researcher.execute(initialState);
                }, executorService);

        // Writer runs after researcher completes (data dependency)
        CompletableFuture<WorkflowState> writerFuture = researchFuture
                .thenApplyAsync(researchedState -> {
                    log.info("[Workflow Async] Writer starting (researcher complete)");
                    return writer.execute(researchedState);
                }, executorService);

        // Critic runs after writer completes
        CompletableFuture<WorkflowState> criticFuture = writerFuture
                .thenApplyAsync(draftedState -> {
                    log.info("[Workflow Async] Critic starting (writer complete)");
                    return critic.execute(draftedState);
                }, executorService);

        // Final step: check critic verdict and do one revision if needed
        return criticFuture.thenApply(criticisedState -> {
            WorkflowState finalState = criticisedState;

            NextAgent verdict = supervisor.decide(criticisedState);
            if (verdict == NextAgent.WRITER) {
                log.info("[Workflow Async] Critic requested revision — running Writer again");
                finalState = writer.execute(criticisedState);
            }

            long totalMs = Instant.now().toEpochMilli() - start;
            String output = finalState.getFinalOutput().isBlank()
                    ? finalState.getWrittenDraft()
                    : finalState.getFinalOutput();

            log.info("[Workflow Async] Complete in {}ms", totalMs);
            return new WorkflowResult(output, finalState.getExecutionLog(), totalMs, "async");
        });
    }

    /**
     * Run MULTIPLE queries in parallel — the real parallel use case.
     *
     * Each query is a completely independent workflow.
     * No data dependencies between queries.
     * All queries run simultaneously on the thread pool.
     *
     * 3 queries sequential: ~45 seconds (3 × 15 seconds each)
     * 3 queries parallel:   ~15 seconds (all run at same time)
     *
     * This is the metric that goes in your README benchmark table.
     */
    public WorkflowBatchResult runBatch(java.util.List<String> queries, String outputFormat) {
        log.info("[Workflow Batch] Running {} queries in parallel", queries.size());
        long start = Instant.now().toEpochMilli();

        // Launch all queries simultaneously
        java.util.List<CompletableFuture<WorkflowResult>> futures = queries.stream()
                .map(query -> {
                    var state = WorkflowState.initial(query, outputFormat,
                            java.util.UUID.randomUUID().toString().substring(0, 8));
                    return runAsync(state);
                })
                .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        java.util.List<WorkflowResult> results = futures.stream()
                .map(f -> f.join())
                .toList();

        long totalMs = Instant.now().toEpochMilli() - start;
        double avgMs = results.stream().mapToLong(WorkflowResult::latencyMs).average().orElse(0);

        return new WorkflowBatchResult(results, totalMs, avgMs, queries.size());
    }

    // ── Result records ────────────────────────────────────────────────────────

    public record WorkflowResult(
            String finalOutput,
            java.util.List<String> executionLog,
            long latencyMs,
            String executionMode
    ) {}

    public record WorkflowBatchResult(
            java.util.List<WorkflowResult> results,
            long totalWallClockMs,
            double avgQueryMs,
            int queryCount
    ) {}
}