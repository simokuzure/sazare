package com.sazare.service.review;

import java.math.BigDecimal;

public record FsrsResult(
        BigDecimal easeFactor,
        int repetitionCount,
        int intervalDays,
        int lapseCount,
        double stability,
        double difficulty,
        int rating
) {
}
