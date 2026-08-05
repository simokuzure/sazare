package com.jt.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record QuestionQueryRequest(
        @Pattern(regexp = "TRANSLATION_ZH_TO_JA", message = "questionType 只能是 TRANSLATION_ZH_TO_JA")
        String questionType,

        @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
        String level,

        @Min(value = 1, message = "difficulty 必须在 1 到 5 之间")
        @Max(value = 5, message = "difficulty 必须在 1 到 5 之间")
        Integer difficulty,

        List<String> tagCodes,

        Boolean spoken,

        Boolean business,

        Boolean exam,

        @Pattern(regexp = "AI|MANUAL|REVIEW_DERIVED", message = "sourceType 只能是 AI、MANUAL 或 REVIEW_DERIVED")
        String sourceType,

        Boolean enabled,

        @Min(value = 1, message = "page 必须大于等于 1")
        Integer page,

        @Min(value = 1, message = "size 必须在 1 到 100 之间")
        @Max(value = 100, message = "size 必须在 1 到 100 之间")
        Integer size
) {
    public QuestionQueryRequest {
        questionType = questionType == null || questionType.isBlank()
                ? "TRANSLATION_ZH_TO_JA"
                : questionType.trim();
        level = level == null || level.isBlank() ? null : level.trim();
        sourceType = sourceType == null || sourceType.isBlank() ? null : sourceType.trim();
        enabled = enabled == null ? true : enabled;
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    public String getQuestionType() {
        return questionType;
    }

    public String getLevel() {
        return level;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public List<String> getTagCodes() {
        return tagCodes;
    }

    public Boolean getSpoken() {
        return spoken;
    }

    public Boolean getBusiness() {
        return business;
    }

    public Boolean getExam() {
        return exam;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public Integer getPage() {
        return page;
    }

    public Integer getSize() {
        return size;
    }
}
