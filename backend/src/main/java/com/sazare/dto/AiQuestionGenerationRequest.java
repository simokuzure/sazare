package com.sazare.dto;

import com.sazare.common.TranslationDirection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AiQuestionGenerationRequest(
        @Min(value = 1, message = "questionCount 必须在 1 到 5 之间")
        @Max(value = 5, message = "questionCount 必须在 1 到 5 之间")
        Integer questionCount,

        @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
        String level,

        @Min(value = 1, message = "difficulty 必须在 1 到 5 之间")
        @Max(value = 5, message = "difficulty 必须在 1 到 5 之间")
        Integer difficulty,

        List<@NotBlank(message = "sceneTagCodes 不能包含空值") String> sceneTagCodes,

        List<@NotBlank(message = "functionTagCodes 不能包含空值") String> functionTagCodes,

        @Size(max = 20, message = "excludedSourceTexts 最多 20 条")
        List<@NotBlank(message = "excludedSourceTexts 不能包含空值") String> excludedSourceTexts,

        @Size(max = 500, message = "extraRequirements 最多 500 个字符")
        String extraRequirements,

        @Pattern(regexp = TranslationDirection.LEARNING_MODE_PATTERN, message = "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA")
        String learningMode
) {
    public AiQuestionGenerationRequest {
        questionCount = questionCount == null ? 1 : questionCount;
        level = normalizeLevel(level);
        difficulty = difficulty == null ? 3 : difficulty;
        learningMode = learningMode == null || learningMode.isBlank() ? "ZH_TO_JA" : learningMode.trim();
    }

    public AiQuestionGenerationRequest(Integer questionCount, String level, Integer difficulty,
                                       List<String> sceneTagCodes, List<String> functionTagCodes,
                                       List<String> excludedSourceTexts, String extraRequirements) {
        this(questionCount, level, difficulty, sceneTagCodes, functionTagCodes,
                excludedSourceTexts, extraRequirements, "ZH_TO_JA");
    }

    private static String normalizeLevel(String level) {
        if (level == null || level.isBlank()) {
            return "N3";
        }
        return level.trim();
    }
}
