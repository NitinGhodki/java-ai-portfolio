package com.aiportfolio.week_b.day11_multiagent.state;

/**
 * NextAgent — type-safe routing enum.
 *
 * Python equivalent: returning "researcher", "writer", "critic", "end"
 * as strings from supervisor_route().
 *
 * Java advantage: typos are compile errors, not runtime bugs.
 * switch(nextAgent) with --enable-preview exhaustiveness checking
 * warns if you add a new value but forget a case.
 *
 * Every routing decision in the workflow uses this enum.
 * No string comparisons anywhere in the coordination code.
 */
public enum NextAgent {
    RESEARCHER,
    WRITER,
    CRITIC,
    END
}