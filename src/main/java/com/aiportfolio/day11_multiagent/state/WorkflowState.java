package com.aiportfolio.day11_multiagent.state;

import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.util.ArrayList;
import java.util.List;

/**
 * WorkflowState — immutable state passed between agents.
 *
 * Python equivalent: WorkflowState TypedDict from Week 2 Day 13.
 *
 * Java difference: @With annotation generates withXxx() methods
 * that return a NEW instance with one field changed.
 * Original state is never mutated.
 *
 * Why immutability matters for multi-agent:
 * If two agents run in parallel and both mutate the same state object,
 * you get a race condition — non-deterministic bugs that are nearly
 * impossible to reproduce.
 * With @With: each agent returns a new state, the supervisor merges
 * them explicitly. No shared mutable state. No race conditions.
 *
 * This is the Java answer to Python's LangGraph Annotated[list, operator.add]
 * reducer pattern — explicit immutable updates instead of reducer functions.
 */
@Data
@Builder
@With
public class WorkflowState {

    private final String userRequest;
    private final String outputFormat;           // "paragraph", "bullets", "table"
    private final String researchFindings;       // set by ResearcherAgent
    private final String writtenDraft;           // set by WriterAgent
    private final String critiqueResult;         // set by CriticAgent
    private final int revisionCount;
    private final String finalOutput;
    private final String sessionId;

    @Builder.Default
    private final List<String> executionLog = new ArrayList<>();

    /**
     * Returns a new state with an additional log entry.
     * Never modifies the existing list — returns new state with new list.
     */
    public WorkflowState withLogEntry(String entry) {
        List<String> newLog = new ArrayList<>(this.executionLog);
        newLog.add(entry);
        return this.withExecutionLog(newLog);
    }

    /**
     * Factory method — creates clean initial state for a new workflow run.
     */
    public static WorkflowState initial(String userRequest, String outputFormat, String sessionId) {
        return WorkflowState.builder()
                .userRequest(userRequest)
                .outputFormat(outputFormat)
                .researchFindings("")
                .writtenDraft("")
                .critiqueResult("")
                .revisionCount(0)
                .finalOutput("")
                .sessionId(sessionId)
                .build();
    }
}