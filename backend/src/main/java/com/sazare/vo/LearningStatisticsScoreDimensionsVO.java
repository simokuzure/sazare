package com.sazare.vo;

import java.math.BigDecimal;

public record LearningStatisticsScoreDimensionsVO(
        BigDecimal grammarVocabularyScore,
        BigDecimal naturalFluencyScore,
        BigDecimal scenarioAdaptationScore,
        BigDecimal informationCompletenessScore
) {
}
