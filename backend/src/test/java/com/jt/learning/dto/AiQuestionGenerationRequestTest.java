package com.jt.learning.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionGenerationRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void validRequestShouldPassValidation() {
        AiQuestionGenerationRequest request = new AiQuestionGenerationRequest(
                1,
                "N4",
                3,
                List.of("DAILY_LIFE_WEATHER"),
                List.of("FUNCTION_PROPOSE_PLAN"),
                List.of("如果明天下雨，我们就在家学习吧。"),
                "偏口语"
        );

        Set<ConstraintViolation<AiQuestionGenerationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void omittedParametersShouldUseDefaultValues() {
        AiQuestionGenerationRequest request = new AiQuestionGenerationRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        Set<ConstraintViolation<AiQuestionGenerationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.questionCount()).isEqualTo(1);
        assertThat(request.level()).isEqualTo("N3");
        assertThat(request.difficulty()).isEqualTo(3);
    }

    @Test
    void blankLevelShouldUseDefaultValue() {
        AiQuestionGenerationRequest request = new AiQuestionGenerationRequest(
                null,
                " ",
                null,
                null,
                null,
                null,
                null
        );

        Set<ConstraintViolation<AiQuestionGenerationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.level()).isEqualTo("N3");
    }

    @Test
    void invalidRequestShouldReturnChineseMessages() {
        AiQuestionGenerationRequest request = new AiQuestionGenerationRequest(
                0,
                "N6",
                6,
                List.of(""),
                List.of(" "),
                List.of(""),
                "x".repeat(501)
        );

        Set<ConstraintViolation<AiQuestionGenerationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "questionCount 必须在 1 到 5 之间",
                        "level 只能是 N5、N4、N3、N2、N1",
                        "difficulty 必须在 1 到 5 之间",
                        "sceneTagCodes 不能包含空值",
                        "functionTagCodes 不能包含空值",
                        "excludedSourceTexts 不能包含空值",
                        "extraRequirements 最多 500 个字符"
                );
    }
}
