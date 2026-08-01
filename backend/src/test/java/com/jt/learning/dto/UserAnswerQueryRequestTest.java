package com.jt.learning.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserAnswerQueryRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldUseDefaultPagination() {
        UserAnswerQueryRequest request = new UserAnswerQueryRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(request.page()).isEqualTo(1);
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    void shouldRejectInvalidQueryValues() {
        UserAnswerQueryRequest request = new UserAnswerQueryRequest(
                "DONE",
                0L,
                "N6",
                new BigDecimal("-1"),
                new BigDecimal("101"),
                0,
                101
        );

        Set<ConstraintViolation<UserAnswerQueryRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains(
                        "answerStatus",
                        "questionId",
                        "level",
                        "minTotalScore",
                        "maxTotalScore",
                        "page",
                        "size"
                );
    }
}
