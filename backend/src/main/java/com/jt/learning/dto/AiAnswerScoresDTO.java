package com.jt.learning.dto;

public record AiAnswerScoresDTO(
        Integer grammarVocabularyScore,
        Integer naturalFluencyScore,
        Integer scenarioAdaptationScore,
        Integer informationCompletenessScore
) {
}
