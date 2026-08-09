package com.jt.learning.dto;

import java.math.BigDecimal;

public record LearningStatisticsScoreDimensionsRow(
        BigDecimal grammarVocabularyScore,
        BigDecimal naturalFluencyScore,
        BigDecimal scenarioAdaptationScore,
        BigDecimal informationCompletenessScore
) {
}
