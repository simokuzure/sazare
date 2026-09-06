package com.sazare.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionControllerValidationTest {

    private static ValidatorFactory validatorFactory;
    private static ExecutableValidator executableValidator;
    private static Method getRandomQuestionsMethod;
    private static QuestionController questionController;

    @BeforeAll
    static void setUpValidator() throws NoSuchMethodException {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        executableValidator = validatorFactory.getValidator().forExecutables();
        getRandomQuestionsMethod = QuestionController.class.getMethod(
                "getRandomQuestions",
                String.class,
                String.class,
                Integer.class,
                List.class,
                Boolean.class,
                Boolean.class,
                Boolean.class,
                String.class,
                Boolean.class,
                Integer.class
        );
        questionController = new QuestionController(null);
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void getRandomQuestionsShouldReturnChineseMessageForInvalidQuestionType() {
        assertValidationMessage(
                randomQuestionParameters("INVALID", null, null),
                "questionType 不合法"
        );
    }

    @Test
    void getRandomQuestionsShouldReturnChineseMessageForInvalidLevel() {
        assertValidationMessage(
                randomQuestionParameters("TRANSLATION_ZH_TO_JA", "N6", null),
                "level 只能是 N5、N4、N3、N2、N1"
        );
    }

    @Test
    void getRandomQuestionsShouldReturnChineseMessageForDifficultyBelowRange() {
        assertValidationMessage(
                randomQuestionParameters("TRANSLATION_ZH_TO_JA", null, 0),
                "difficulty 必须在 1 到 5 之间"
        );
    }

    @Test
    void getRandomQuestionsShouldReturnChineseMessageForDifficultyAboveRange() {
        assertValidationMessage(
                randomQuestionParameters("TRANSLATION_ZH_TO_JA", null, 6),
                "difficulty 必须在 1 到 5 之间"
        );
    }

    @Test
    void getRandomQuestionsShouldReturnChineseMessageForCountBelowRange() {
        assertValidationMessage(
                randomQuestionParameters("TRANSLATION_ZH_TO_JA", null, null, 0),
                "count 必须在 1 到 5 之间"
        );
    }

    @Test
    void getRandomQuestionsShouldReturnChineseMessageForCountAboveRange() {
        assertValidationMessage(
                randomQuestionParameters("TRANSLATION_ZH_TO_JA", null, null, 6),
                "count 必须在 1 到 5 之间"
        );
    }

    private static Object[] randomQuestionParameters(String questionType, String level, Integer difficulty) {
        return randomQuestionParameters(questionType, level, difficulty, 1);
    }

    private static Object[] randomQuestionParameters(
            String questionType,
            String level,
            Integer difficulty,
            Integer count
    ) {
        return new Object[]{questionType, level, difficulty, null, null, null, null, null, true, count};
    }

    private static void assertValidationMessage(Object[] parameters, String expectedMessage) {
        Set<ConstraintViolation<QuestionController>> violations = executableValidator.validateParameters(
                questionController,
                getRandomQuestionsMethod,
                parameters
        );

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(expectedMessage);
    }
}
