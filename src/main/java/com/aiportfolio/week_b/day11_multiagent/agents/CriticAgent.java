package com.aiportfolio.week_b.day11_multiagent.agents;

import com.aiportfolio.week_b.day11_multiagent.state.WorkflowState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * CriticAgent — reviews output for accuracy and quality.
 *
 * Single responsibility: given research (ground truth) and draft,
 * verify accuracy and return APPROVED or NEEDS_REVISION with specific issues.
 *
 * Temperature: 0.0 — strictest possible. Critic must be deterministic.
 *
 * The Critic's verdict directly controls the Supervisor's routing.
 * APPROVED → workflow ends.
 * NEEDS_REVISION → Writer runs again with issues listed.
 */
@Slf4j
@Component
public class CriticAgent {

//    @SystemMessage("""
//            You are a Critic Agent. Your ONLY job is to verify accuracy and quality.
//            Rules:
//            - Your response MUST start with either APPROVED or NEEDS_REVISION
//            - Check every factual claim in the draft against the research findings
//            - Be specific: not "it's wrong" but "price stated is X, actual price is Y"
//            - If NEEDS_REVISION: list each issue numbered clearly
//            - If all facts are correct and format matches request: respond APPROVED
//            """)
//    interface CriticService {
//        String review(@UserMessage String reviewTask);
//    }

    @Data
    public static class CriticReview {
        private boolean approved; // True if APPROVED, False if NEEDS_REVISION
        private String feedbackDetails; // The actual verification notes / list of fixes
    }

    @SystemMessage("""
    You are a Critic Agent. Your ONLY job is to verify accuracy and quality.
    
    Analyze the draft against the research findings and populate the required fields.
    If there are mismatches, set status to NEEDS_REVISION and list the required fixes.
    If everything matches perfectly, set status to APPROVED.
    """)
    interface CriticService {
        CriticReview review(@UserMessage String reviewTask);
    }

    private final CriticService service;

    public CriticAgent(ChatModel model) {
        this.service = AiServices.create(CriticService.class, model);
    }

    public WorkflowState execute(WorkflowState state) {
        log.info("[Critic] Reviewing draft for session {}", state.getSessionId());

        long start = System.currentTimeMillis();
        String task = String.format("""
                Review this output for factual accuracy.
                
                ORIGINAL REQUEST: %s
                
                RESEARCH FINDINGS (ground truth):
                %s
                
                WRITTEN OUTPUT (to review):
                %s
                
                Check: does every fact in the output match the research?
                Are all numbers correct? Is the format as requested?
                """,
                state.getUserRequest(),
                state.getResearchFindings(),
                state.getWrittenDraft()
        );

        CriticReview critique = service.review(task);
        long latency = System.currentTimeMillis() - start;

        boolean approved = critique.isApproved();
        log.info("[Critic] Verdict: {} in {}ms",
                approved ? "✓ APPROVED" : "✗ NEEDS_REVISION", latency);

        return state
                .withCritiqueResult(critique.getFeedbackDetails())
                .withFinalOutput(approved ? state.getWrittenDraft() : "")
                .withLogEntry(String.format(
                        "Critic: %s in %dms", approved ? "APPROVED" : "NEEDS_REVISION", latency
                ));
    }
}