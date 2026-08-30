package com.sazare.dto;

public record AiAnswerScoresDTO(
        Integer grammarVocabularyScore,
        Integer naturalFluencyScore,
        Integer scenarioAdaptationScore,
        Integer informationCompletenessScore
) {
}
