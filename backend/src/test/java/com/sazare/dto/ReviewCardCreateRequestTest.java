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

class ReviewCardCreateRequestTest {

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
        ReviewCardCreateRequest request = new ReviewCardCreateRequest(
                "练习更自然的移动表达",
                "明日の午後、公園を散歩します。",
                0,
                null
        );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void invalidRequestShouldReturnChineseMessages() {
        ReviewCardCreateRequest request = new ReviewCardCreateRequest(
                "x".repeat(129),
                "x".repeat(2001),
                -1,
                "x".repeat(1001)
        );

        Set<ConstraintViolation<ReviewCardCreateRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "复习重点长度不能超过 128",
                        "目标日语表达长度不能超过 2000",
                        "中文原句索引不能小于 0",
                        "复习题中文长度不能超过 1000"
                );
    }

    @Test
    void blankRequiredFieldsShouldBeRejected() {
        ReviewCardCreateRequest request = new ReviewCardCreateRequest(" ", "", null, null);

        assertThat(validator.validate(request))
                .extracting(ConstraintViolation::getMessage)
                .contains("复习重点不能为空", "目标日语表达不能为空");
    }
}
