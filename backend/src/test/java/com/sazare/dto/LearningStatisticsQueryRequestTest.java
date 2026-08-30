package com.sazare.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LearningStatisticsQueryRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldUseLastThirtyDaysByDefault() {
        LearningStatisticsQueryRequest request = new LearningStatisticsQueryRequest(null, null, null);

        assertThat(request.range()).isEqualTo("LAST_30_DAYS");
    }

    @Test
    void shouldRejectUnsupportedRange() {
        LearningStatisticsQueryRequest request = new LearningStatisticsQueryRequest("ALL", null, null);

        Set<ConstraintViolation<LearningStatisticsQueryRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .contains("range");
    }
}
