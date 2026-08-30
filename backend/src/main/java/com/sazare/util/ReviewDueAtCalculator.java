package com.sazare.util;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/** 统一计算复习卡片的下次到期时间。 */
public final class ReviewDueAtCalculator {

    private static final LocalTime REVIEW_START_TIME = LocalTime.of(7, 0);

    private ReviewDueAtCalculator() {
    }

    public static LocalDateTime calculate(LocalDateTime occurredAt, int intervalDays) {
        Objects.requireNonNull(occurredAt, "发生时间不能为空");
        if (intervalDays < 1) {
            throw new IllegalArgumentException("复习间隔天数必须大于等于1");
        }
        return occurredAt.toLocalDate().plusDays(intervalDays).atTime(REVIEW_START_TIME);
    }
}
