package com.aiportfolio.week_b.day11_multiagent.agents;

import com.aiportfolio.week_b.day11_multiagent.state.WorkflowState;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WriterAgent — structures and formats research into readable output.
 *
 * Single responsibility: given research findings and a format request,
 * produce a well-structured response. Does NOT search for new information.
 *
 * Temperature: 0.3 — slight creativity for writing quality,
 * but not so high that it invents facts.
 *
 * Revision awareness: if critique exists in state, the writing task
 * includes the critique issues so the Writer can address them.
 */
@Slf4j
@Component
public class WriterAgent {

    @SystemMessage("""
            You are a Writer Agent. Your ONLY job is to structure and format information clearly.
            Rules:
            - Use ONLY the research findings provided — never invent facts
            - Match the requested output format exactly
            - If data is insufficient, say "INSUFFICIENT_DATA: <what is missing>"
            - Keep all numbers and facts exactly as stated in the research
            """)
    interface WriterService {
        String write(@UserMessage String writingTask);
    }

    private final WriterService service;

    public WriterAgent(ChatModel model) {
        this.service = AiServices.create(WriterService.class, model);
    }

    public WorkflowState execute(WorkflowState state) {
        int revision = state.getRevisionCount();
        log.info("[Writer] {} for session {}",
                revision == 0 ? "Writing first draft" : "Revision " + revision,
                state.getSessionId());

        long start = System.currentTimeMillis();

        String critique = state.getCritiqueResult();
        String revisionNote = "";
        if (!critique.isBlank() && critique.toUpperCase().contains("NEEDS_REVISION")) {
            revisionNote = "\n\nPREVIOUS DRAFT HAD ISSUES — FIX ALL OF THESE:\n" + critique;
        }

        String task = String.format(
                "Format the following research as %s.\n\nResearch:\n%s%s",
                state.getOutputFormat(),
                state.getResearchFindings(),
                revisionNote
        );

        String draft = service.write(task);
        long latency = System.currentTimeMillis() - start;

        log.info("[Writer] Complete in {}ms. Draft: {}...",
                latency, draft.substring(0, Math.min(80, draft.length())));

        return state
                .withWrittenDraft(draft)
                .withCritiqueResult("")         // clear previous critique
                .withRevisionCount(revision + 1)
                .withLogEntry(String.format(
                        "Writer rev%d: %d chars in %dms", revision + 1, draft.length(), latency
                ));
    }
}