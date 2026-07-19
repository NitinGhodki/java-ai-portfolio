package com.aiportfolio.week_a.day5.structured;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * ExtractionModels — Java records used as extraction targets.
 *
 * These records define WHAT you want to extract.
 * LangChain4j reads the field names and types to build JSON schemas.
 * Spring AI's BeanOutputConverter does the same.
 *
 * Python equivalent: your Pydantic models from Week 1 Day 2:
 *   class Person(BaseModel):
 *       name: str
 *       age: Optional[int] = None
 *       skills: list[str] = []
 *
 * Java records are immutable — no setters, just a constructor and getters.
 * Perfect for extraction targets: you receive data, you don't modify it.
 *
 * @JsonPropertyDescription helps LangChain4j generate better prompts.
 * Same role as Pydantic's Field(description="...").
 */

public class ExtractionModels {

    // ── Person extraction

    public record Person(
            @JsonPropertyDescription("Full name of the person")
            String name,

            @JsonPropertyDescription("Age as an integer, null if not mentioned")
            Integer age,

            @JsonPropertyDescription("Professional occupation or job title")
            String occupation,

            @JsonPropertyDescription("List of technical or professional skills mentioned")
            List<String> skills
    ) {}

    // Job posting extraction

    public record JobPosting(
            @JsonPropertyDescription("Exact job title as stated")
            String jobTitle,

            @JsonPropertyDescription("Company name")
            String company,

            @JsonPropertyDescription("City or remote")
            String location,

            @JsonPropertyDescription("Minimum years of experience required, null if not specified")
            Integer minExperienceYears,

            @JsonPropertyDescription("List of required technical skills")
            List<String> requiredSkills,

            @JsonPropertyDescription("Salary range as a string, null if not mentioned")
            String salaryRange
    ) {}

    // Support ticket classification

    public record SupportTicket(
            @JsonPropertyDescription("Category: one of billing, technical, account, general")
            String category,

            @JsonPropertyDescription("Priority: one of low, medium, high, urgent")
            String priority,

            @JsonPropertyDescription("One sentence summary of the issue")
            String summary,

            @JsonPropertyDescription("Sentiment: one of positive, neutral, negative, angry")
            String sentiment,

            @JsonPropertyDescription("True if issue requires immediate escalation")
            boolean requiresEscalation
    ) {}

    // Product review analysis

    public record ProductReview(
            @JsonPropertyDescription("Product name or description being reviewed")
            String product,

            @JsonPropertyDescription("Overall rating from 1 to 5")
            int rating,

            @JsonPropertyDescription("List of positive aspects mentioned")
            List<String> positives,

            @JsonPropertyDescription("List of negative aspects or complaints")
            List<String> negatives,

            @JsonPropertyDescription("True if reviewer recommends the product")
            boolean recommends,

            @JsonPropertyDescription("Key insight in one sentence")
            String keyInsight
    ) {}

}
