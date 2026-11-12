package com.sazare.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCardQueryRequestTest {

    @Test
    void oldConstructorsAndMissingFiltersStillRequestAllActiveCards() {
        var request = new ReviewCardQueryRequest(null, null, null, null);
        assertThat(request.status()).isEqualTo("ACTIVE");
        assertThat(request.dueOnly()).isFalse();
        assertThat(request.waitingOnly()).isFalse();
        assertThat(new ReviewCardQueryRequest("ACTIVE", false, 1, 20, "EN_TO_JA").waitingOnly()).isFalse();
    }

    @Test
    void rejectsConflictingDueFiltersAndWaitingMasteredCards() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new ReviewCardQueryRequest("ACTIVE", true, true, 1, 20, "ZH_TO_JA")))
                    .extracting(violation -> violation.getMessage()).contains("dueOnly 与 waitingOnly 不能同时为 true");
            assertThat(validator.validate(new ReviewCardQueryRequest("MASTERED", false, true, 1, 20, "ZH_TO_JA")))
                    .extracting(violation -> violation.getMessage()).contains("waitingOnly 仅适用于 ACTIVE 状态");
            assertThat(validator.validate(new ReviewCardQueryRequest("ACTIVE", false, true, 1, 20, "ZH_TO_JA"))).isEmpty();
        }
    }
}
