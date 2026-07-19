package com.aiportfolio.week_b.day11_multiagent.agents;

import com.aiportfolio.week_b.day11_multiagent.state.WorkflowState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ResearcherAgent — finds and gathers information.
 *
 * Single responsibility: given a topic, return raw research findings.
 * Does NOT format, summarise, or evaluate. Only researches.
 *
 * Python equivalent: ResearcherAgent from Week 2 Day 13
 * with search_knowledge_base and calculator tools.
 *
 * Java difference: no tool injection today (keeping focus on
 * multi-agent coordination pattern). Adding tools: same @Tool
 * pattern from Day 6.
 *
 * Temperature: 0.0 — research needs factual accuracy, not creativity.
 */
@Slf4j
@Component
public class ResearcherAgent {

    @SystemMessage("""
            You are a Research Agent. Your ONLY job is to find and report facts.
            Rules:
            - Report findings with specific numbers, dates, and terms
            - Do NOT format or summarise — report raw findings
            - If you do not know something, say "NOT FOUND" explicitly
            - Never invent or guess information
            """)
    interface ResearcherService {
        String research(@UserMessage String researchTask);
    }

    private final ResearcherService service;

    public ResearcherAgent(ChatModel model) {
        this.service = AiServices.create(ResearcherService.class, model);
    }

    /**
     * Execute research task and return updated state.
     *
     * Returns a new WorkflowState with researchFindings populated.
     * Original state is unchanged (immutable pattern).
     */
    public WorkflowState execute(WorkflowState state) {
        log.info("[Researcher] Starting research for: {}",
                state.getUserRequest().substring(0, Math.min(50, state.getUserRequest().length())));

        long start = System.currentTimeMillis();
        String task = String.format(
                "Research this topic thoroughly and report all relevant facts:\n%s\n\n" +
                        "Focus on: specific numbers, policies, technical specifications, and comparisons.",
                state.getUserRequest()
        );

        String findings = service.research(task);
        long latency = System.currentTimeMillis() - start;

        log.info("[Researcher] Complete in {}ms. Findings: {}...",
                latency, findings.substring(0, Math.min(80, findings.length())));

        return state
                .withResearchFindings(findings)
                .withLogEntry(String.format(
                        "Researcher: %d chars in %dms", findings.length(), latency
                ));
    }
}