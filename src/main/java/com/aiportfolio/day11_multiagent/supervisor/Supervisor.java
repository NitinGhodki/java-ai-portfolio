package com.aiportfolio.day11_multiagent.supervisor;

import com.aiportfolio.day11_multiagent.state.NextAgent;
import com.aiportfolio.day11_multiagent.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Supervisor — orchestrates agents by reading state and deciding next step.
 *
 * Python equivalent: supervisor_route() from Week 2 Day 13.
 *
 * Java advantages over Python string routing:
 * 1. Return type is NextAgent enum — type-safe, not a string
 * 2. switch expression is exhaustive — compiler warns on missing cases
 * 3. Unit testable without Spring context — pure Java logic
 * 4. Rename NextAgent.RESEARCHER everywhere: one IDE refactoring operation
 *
 * No LLM call: routing is deterministic rule-based logic.
 * Same justification as Python Week 2: simple state machine routing
 * does not need LLM reasoning and adding LLM creates hallucination risk.
 */
@Slf4j
@Component
public class Supervisor {

    private static final int MAX_REVISIONS = 2;

    /**
     * Decide which agent runs next based on current workflow state.
     * Called after every agent completes.
     */
    public NextAgent decide(WorkflowState state) {
        NextAgent decision = computeDecision(state);
        log.info("[Supervisor] State analysis → routing to: {}", decision);
        return decision;
    }

    private NextAgent computeDecision(WorkflowState state) {
        // Safety valve — prevent infinite revision loops
        if (state.getRevisionCount() >= MAX_REVISIONS) {
            log.info("[Supervisor] Max revisions ({}) reached → END", MAX_REVISIONS);
            return NextAgent.END;
        }

        // No research yet → start with Researcher
        if (isBlank(state.getResearchFindings())) {
            log.debug("[Supervisor] No research → RESEARCHER");
            return NextAgent.RESEARCHER;
        }

        // Research done, no draft → write it
        if (isBlank(state.getWrittenDraft())) {
            log.debug("[Supervisor] Research ready, no draft → WRITER");
            return NextAgent.WRITER;
        }

        // Draft exists, not yet reviewed → review it
        if (isBlank(state.getCritiqueResult())) {
            log.debug("[Supervisor] Draft ready, no critique → CRITIC");
            return NextAgent.CRITIC;
        }

        // Critique exists — act on verdict
        String critique = state.getCritiqueResult().toUpperCase();
        if (critique.startsWith("APPROVED")) {
            log.info("[Supervisor] Critique APPROVED → END");
            return NextAgent.END;
        }

        if (critique.contains("NEEDS_REVISION")) {
            log.info("[Supervisor] NEEDS_REVISION → WRITER for revision {}",
                    state.getRevisionCount() + 1);
            return NextAgent.WRITER;
        }

        log.warn("[Supervisor] Unrecognised critique verdict → END (safety)");
        return NextAgent.END;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}