package com.sazare.service.review;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTargetPolicyTest {

    @ParameterizedTest
    @CsvSource({"LOW,6", "MEDIUM,8", "HIGH,10"})
    void mapsSeverityToFixedBase(String severity, int expected) {
        assertThat(ReviewTargetPolicy.baseTarget(severity)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"6,0,6", "6,1,7", "6,2,9", "6,3,12", "6,4,12",
            "8,0,8", "8,1,9", "8,2,11", "8,3,14", "8,4,16",
            "10,0,10", "10,1,11", "10,2,13", "10,3,16", "10,4,20", "10,5,20",
            "3,2,3", "4,2,4", "5,2,5"})
    void calculatesCappedTargetAndPreservesLegacyHistory(int base, int failures, int expected) {
        assertThat(ReviewTargetPolicy.effectiveTarget(base, failures)).isEqualTo(expected);
    }

    @Test
    void largeFailureCountCannotOverflowOrEscapeCap() {
        assertThat(ReviewTargetPolicy.effectiveTarget(10, Integer.MAX_VALUE)).isEqualTo(20);
    }
}
