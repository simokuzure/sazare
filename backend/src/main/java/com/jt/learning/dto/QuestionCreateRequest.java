package com.jt.learning.dto;

import com.jt.learning.common.TranslationDirection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionCreateRequest(
        @NotBlank(message = "questionType 不能为空")
        @Pattern(regexp = TranslationDirection.SHORT_QUESTION_TYPE_PATTERN, message = "新建题目只能是中译日或英译日短句")
        String questionType,

        @NotBlank(message = "sourceText 不能为空")
        String sourceText,

        @NotBlank(message = "contextText 不能为空")
        String contextText,

        @NotBlank(message = "level 不能为空")
        @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
        String level,

        @NotNull(message = "difficulty 不能为空")
        @Min(value = 1, message = "difficulty 必须在 1 到 5 之间")
        @Max(value = 5, message = "difficulty 必须在 1 到 5 之间")
        Integer difficulty,

        @NotBlank(message = "grammarPoint 不能为空")
        String grammarPoint,

        @NotNull(message = "spoken 不能为空")
        Boolean spoken,

        @NotNull(message = "business 不能为空")
        Boolean business,

        @NotNull(message = "exam 不能为空")
        Boolean exam,

        @NotEmpty(message = "tagCodes 不能为空")
        @Size(max = 20, message = "tagCodes 最多 20 个")
        List<@NotBlank(message = "tagCodes 不能包含空值") String> tagCodes,

        @NotEmpty(message = "answers 不能为空")
        @Size(max = 10, message = "answers 最多 10 个")
        List<@Valid QuestionAnswerRequest> answers
) {
}
