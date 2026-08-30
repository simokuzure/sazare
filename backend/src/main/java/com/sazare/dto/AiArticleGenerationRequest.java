package com.sazare.dto;

import com.sazare.common.ArticleLengthTier;
import com.sazare.common.TranslationDirection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiArticleGenerationRequest(
        @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
        String level,

        @Min(value = 1, message = "difficulty 必须在 1 到 5 之间")
        @Max(value = 5, message = "difficulty 必须在 1 到 5 之间")
        Integer difficulty,

        String genreTagCode,

        @Size(max = 100, message = "topic 最多 100 个字符")
        String topic,

        @Size(max = 500, message = "extraRequirements 最多 500 个字符")
        String extraRequirements,

        @Pattern(regexp = TranslationDirection.LEARNING_MODE_PATTERN, message = "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA")
        String learningMode,

        @Pattern(regexp = ArticleLengthTier.PATTERN, message = "lengthTier 只能是 SHORT、MEDIUM 或 LONG")
        String lengthTier
) {
    public AiArticleGenerationRequest {
        level = level == null || level.isBlank() ? "N3" : level.trim();
        difficulty = difficulty == null ? 3 : difficulty;
        genreTagCode = genreTagCode == null || genreTagCode.isBlank() ? null : genreTagCode.trim();
        topic = topic == null || topic.isBlank() ? null : topic.trim();
        extraRequirements = extraRequirements == null || extraRequirements.isBlank()
                ? null
                : extraRequirements.trim();
        learningMode = learningMode == null || learningMode.isBlank() ? "ZH_TO_JA" : learningMode.trim();
        lengthTier = lengthTier == null || lengthTier.isBlank() ? "MEDIUM" : lengthTier.trim();
    }

    public AiArticleGenerationRequest(String level, Integer difficulty, String genreTagCode,
                                      String topic, String extraRequirements) {
        this(level, difficulty, genreTagCode, topic, extraRequirements, "ZH_TO_JA", "MEDIUM");
    }

    public AiArticleGenerationRequest(String level, Integer difficulty, String genreTagCode,
                                      String topic, String extraRequirements, String learningMode) {
        this(level, difficulty, genreTagCode, topic, extraRequirements, learningMode, "MEDIUM");
    }
}
