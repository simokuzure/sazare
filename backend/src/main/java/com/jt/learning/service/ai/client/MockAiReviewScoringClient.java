package com.jt.learning.service.ai.client;

import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.AiReviewScoringClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public class MockAiReviewScoringClient implements AiReviewScoringClient {

    private final ObjectMapper objectMapper;

    public MockAiReviewScoringClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String scoreAnswer(AiQuestionPrompt prompt) {
        boolean english = prompt.systemPrompt().startsWith("The source language");
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "review", Map.of(
                            "quality", 4,
                            "targetErrorResolved", true,
                            "feedback", english
                                    ? "The review focus has been mastered and the expression is mostly natural."
                                    : "复习重点已经掌握，表达基本自然。",
                            "scores", Map.of(
                                    "grammarVocabularyScore", 84,
                                    "naturalFluencyScore", 82,
                                    "scenarioAdaptationScore", 80,
                                    "informationCompletenessScore", 86
                            ),
                            "errorAnalysis", List.of()
                    )
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 复习评分 JSON 序列化失败", exception);
        }
    }
}
