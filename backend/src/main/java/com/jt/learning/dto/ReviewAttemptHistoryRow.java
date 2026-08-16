package com.jt.learning.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewAttemptHistoryRow {

    private Long id;

    private Integer cycleNo;

    private String questionRole;

    private String sourceText;

    private String referenceAnswer;

    private String answerText;

    private String result;

    private BigDecimal totalScore;

    private Integer quality;

    private LocalDateTime createdAt;
}
