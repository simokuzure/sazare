package com.sazare.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewCycleProgressRow {

    private Integer originalQuestionCount;
    private Integer originalPassedCount;
    private Integer retryQuestionCount;
    private Integer pendingQuestionCount;
    private Integer activeQuestionCount;
    private Integer derivedQuestionCount;
    private Integer verifiedDerivedPassedCount;
}
