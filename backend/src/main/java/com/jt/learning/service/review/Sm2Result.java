package com.jt.learning.service.review;

import java.math.BigDecimal;

public record Sm2Result(
        BigDecimal easeFactor,
        int repetitionCount,
        int intervalDays,
        int lapseCount
) {
}
