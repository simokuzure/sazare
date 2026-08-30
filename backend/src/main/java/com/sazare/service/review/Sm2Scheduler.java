package com.sazare.service.review;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class Sm2Scheduler {

    private static final BigDecimal MINIMUM_EASE_FACTOR = new BigDecimal("1.3000");

    public Sm2Result schedule(
            BigDecimal currentEaseFactor,
            int currentRepetitionCount,
            int currentIntervalDays,
            int currentLapseCount,
            int quality
    ) {
        if (quality < 0 || quality > 5) {
            throw new IllegalArgumentException("SM-2质量分必须在0到5之间");
        }

        BigDecimal updatedEaseFactor = calculateEaseFactor(currentEaseFactor, quality);
        if (quality < 3) {
            return new Sm2Result(updatedEaseFactor, 0, 1, currentLapseCount + 1);
        }

        int nextInterval = switch (currentRepetitionCount) {
            case 0 -> 1;
            case 1 -> 6;
            default -> Math.max(1, currentEaseFactor
                    .multiply(BigDecimal.valueOf(currentIntervalDays))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValueExact());
        };
        return new Sm2Result(updatedEaseFactor, currentRepetitionCount + 1, nextInterval, currentLapseCount);
    }

    private BigDecimal calculateEaseFactor(BigDecimal currentEaseFactor, int quality) {
        BigDecimal difference = BigDecimal.valueOf(5L - quality);
        BigDecimal penalty = difference.multiply(
                new BigDecimal("0.08").add(difference.multiply(new BigDecimal("0.02")))
        );
        BigDecimal calculated = currentEaseFactor.add(new BigDecimal("0.1")).subtract(penalty);
        return calculated.max(MINIMUM_EASE_FACTOR).setScale(4, RoundingMode.HALF_UP);
    }
}
