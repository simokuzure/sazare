package com.jt.learning.service;

import java.math.BigDecimal;

public record Sm2Result(
        BigDecimal easeFactor,
        int repetitionCount,
        int intervalDays,
        int lapseCount
) {
}
