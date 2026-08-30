package com.sazare.service.review;

import java.math.BigDecimal;

public record Sm2Result(
        BigDecimal easeFactor,
        int repetitionCount,
        int intervalDays,
        int lapseCount
) {
}
