package com.sazare.service.review;

public final class ReviewTargetPolicy {

    private ReviewTargetPolicy() {
    }

    public static int baseTarget(String severity) {
        return switch (severity) {
            case "LOW" -> 6;
            case "HIGH" -> 10;
            default -> 8;
        };
    }

    public static int effectiveTarget(int baseTarget, int failureCount) {
        if (baseTarget < 1 || failureCount < 0) {
            throw new IllegalArgumentException("基础目标必须为正数，失败次数不能为负数");
        }
        // 3、4、5 仅用于已完成的旧周期，保留当时的固定目标。
        if (baseTarget <= 5) {
            return baseTarget;
        }
        long failures = failureCount;
        int extra = (int) Math.min(failures * (failures + 1) / 2, baseTarget);
        return baseTarget + extra;
    }
}
