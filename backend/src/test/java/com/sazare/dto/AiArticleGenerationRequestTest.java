package com.sazare.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiArticleGenerationRequestTest {

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
    void omittedLengthTierShouldDefaultToMedium() {
        AiArticleGenerationRequest request = new AiArticleGenerationRequest(
                null, null, null, null, null, null, null
        );

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.level()).isEqualTo("N3");
        assertThat(request.difficulty()).isEqualTo(3);
        assertThat(request.lengthTier()).isEqualTo("MEDIUM");
    }

    @Test
    void allLengthTiersShouldPassValidation() {
        for (String tier : Set.of("SHORT", "MEDIUM", "LONG")) {
            AiArticleGenerationRequest request = new AiArticleGenerationRequest(
                    "N3", 3, null, null, null, "ZH_TO_JA", tier
            );
            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Test
    void unsupportedLengthTierShouldReturnChineseMessage() {
        AiArticleGenerationRequest request = new AiArticleGenerationRequest(
                "N3", 3, null, null, null, "ZH_TO_JA", "EXTRA_LONG"
        );

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("lengthTier 只能是 SHORT、MEDIUM 或 LONG");
    }
}
