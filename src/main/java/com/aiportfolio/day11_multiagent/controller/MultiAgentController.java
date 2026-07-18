package com.aiportfolio.day11_multiagent.controller;

import com.aiportfolio.day11_multiagent.state.WorkflowState;
import com.aiportfolio.day11_multiagent.workflow.MultiAgentWorkflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MultiAgentController — REST API for the multi-agent workflow.
 *
 * Three endpoints:
 * POST /api/agents/run          — sequential execution
 * POST /api/agents/run/async    — async execution (non-blocking)
 * POST /api/agents/run/batch    — multiple queries in parallel
 */
@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class MultiAgentController {

    private final MultiAgentWorkflow workflow;

    public record RunRequest(
            String question,
            String outputFormat  // "paragraph", "bullets", "table"
    ) {}

    public record BatchRequest(
            List<String> questions,
            String outputFormat
    ) {}

    /**
     * POST /api/agents/run
     * Run sequential workflow. Blocks until complete (~30-60 seconds).
     *
     * curl -X POST http://localhost:8080/api/agents/run \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "What are all pricing plans?", "outputFormat": "bullets"}'
     */
    @PostMapping("/run")
    public ResponseEntity<MultiAgentWorkflow.WorkflowResult> run(
            @RequestBody RunRequest req) {

        var state = WorkflowState.initial(
                req.question(),
                req.outputFormat() != null ? req.outputFormat() : "paragraph",
                UUID.randomUUID().toString().substring(0, 8)
        );

        var result = workflow.runSequential(state);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/agents/run/async
     * Starts workflow, returns immediately with a future.
     * In a real app: return a job ID, poll for status.
     * Here: we wait for completion but on a separate thread.
     *
     * curl -X POST http://localhost:8080/api/agents/run/async \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "Compare plans and calculate annual cost", "outputFormat": "table"}'
     */
    @PostMapping("/run/async")
    public CompletableFuture<ResponseEntity<MultiAgentWorkflow.WorkflowResult>> runAsync(
            @RequestBody RunRequest req) {

        var state = WorkflowState.initial(
                req.question(),
                req.outputFormat() != null ? req.outputFormat() : "paragraph",
                UUID.randomUUID().toString().substring(0, 8)
        );

        return workflow.runAsync(state)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * POST /api/agents/run/batch
     * Run multiple questions simultaneously.
     * Demonstrates the latency benefit of parallel execution.
     *
     * curl -X POST http://localhost:8080/api/agents/run/batch \
     *   -H "Content-Type: application/json" \
     *   -d '{
     *     "questions": [
     *       "What is the Starter plan price?",
     *       "What is the refund policy?",
     *       "What are the API rate limits?"
     *     ],
     *     "outputFormat": "paragraph"
     *   }'
     */
    @PostMapping("/run/batch")
    public ResponseEntity<MultiAgentWorkflow.WorkflowBatchResult> runBatch(
            @RequestBody BatchRequest req) {

        if (req.questions() == null || req.questions().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var result = workflow.runBatch(
                req.questions(),
                req.outputFormat() != null ? req.outputFormat() : "paragraph"
        );
        return ResponseEntity.ok(result);
    }
}