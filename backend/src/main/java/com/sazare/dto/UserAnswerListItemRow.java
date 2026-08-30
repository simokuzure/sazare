package com.sazare.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class UserAnswerListItemRow {

    private Long id;

    private Long questionId;

    private String learningMode;

    private String questionType;

    private String sourceText;

    private String level;

    private Integer difficulty;

    private String answerText;

    private String answerStatus;

    private Integer grammarVocabularyScore;

    private Integer naturalFluencyScore;

    private Integer scenarioAdaptationScore;

    private Integer informationCompletenessScore;

    private BigDecimal totalScore;

    private String revisedText;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
