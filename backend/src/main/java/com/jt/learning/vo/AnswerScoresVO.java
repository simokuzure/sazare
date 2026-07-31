package com.jt.learning.vo;

public record AnswerScoresVO(
        Integer grammarVocabularyScore,
        Integer naturalFluencyScore,
        Integer scenarioAdaptationScore,
        Integer informationCompletenessScore
) {
}
