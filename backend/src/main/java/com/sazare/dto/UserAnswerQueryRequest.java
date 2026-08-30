package com.sazare.dto;

import com.sazare.common.TranslationDirection;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UserAnswerQueryRequest(
        @Pattern(regexp = "SUBMITTED|REVIEWED|FAILED", message = "answerStatus 只能是 SUBMITTED、REVIEWED、FAILED")
        String answerStatus,

        @Pattern(
                regexp = TranslationDirection.QUESTION_TYPE_PATTERN + "|JAPANESE_CORRECTION",
                message = "questionType 不合法"
        )
        String questionType,

        @Positive(message = "questionId 必须大于 0")
        Long questionId,

        @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
        String level,

        @DecimalMin(value = "0", message = "minTotalScore 必须大于等于 0")
        @DecimalMax(value = "100", message = "minTotalScore 必须小于等于 100")
        BigDecimal minTotalScore,

        @DecimalMin(value = "0", message = "maxTotalScore 必须大于等于 0")
        @DecimalMax(value = "100", message = "maxTotalScore 必须小于等于 100")
        BigDecimal maxTotalScore,

        @Min(value = 1, message = "page 必须大于等于 1")
        Integer page,

        @Min(value = 1, message = "size 必须在 1 到 100 之间")
        @Max(value = 100, message = "size 必须在 1 到 100 之间")
        Integer size,

        @Pattern(regexp = TranslationDirection.LEARNING_MODE_PATTERN, message = "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA")
        String learningMode
) {
    public UserAnswerQueryRequest {
        answerStatus = answerStatus == null || answerStatus.isBlank() ? null : answerStatus.trim();
        questionType = questionType == null || questionType.isBlank() ? null : questionType.trim();
        level = level == null || level.isBlank() ? null : level.trim();
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
        learningMode = learningMode == null || learningMode.isBlank() ? "ZH_TO_JA" : learningMode.trim();
    }

    public UserAnswerQueryRequest(
            String answerStatus,
            Long questionId,
            String level,
            BigDecimal minTotalScore,
            BigDecimal maxTotalScore,
            Integer page,
            Integer size
    ) {
        this(answerStatus, null, questionId, level, minTotalScore, maxTotalScore, page, size, "ZH_TO_JA");
    }

    public String getAnswerStatus() {
        return answerStatus;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getLevel() {
        return level;
    }

    public BigDecimal getMinTotalScore() {
        return minTotalScore;
    }

    public BigDecimal getMaxTotalScore() {
        return maxTotalScore;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }
}
