package com.jt.learning.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewCardListRow {

    private Long id;
    private Long userErrorTypeId;
    private Long errorTypeId;
    private String errorTypeCode;
    private String errorTypeName;
    private String userErrorTypeName;
    private String userErrorTypeDescription;
    private String status;
    private LocalDateTime dueAt;
    private Integer cycleNo;
    private Integer successfulReviewCount;
    private Integer failedReviewCount;
    private Integer targetSuccessCount;
    private Integer originalQuestionCount;
    private Integer originalPassedCount;
    private Integer retryQuestionCount;
    private Integer pendingQuestionCount;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime masteredAt;
}
