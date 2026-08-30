package com.sazare.vo;

import java.time.LocalDateTime;

public record ReviewCardListVO(
        Long id,
        Long userErrorTypeId,
        Long errorTypeId,
        String errorTypeCode,
        String errorTypeName,
        String userErrorTypeName,
        String userErrorTypeDescription,
        String status,
        LocalDateTime dueAt,
        ReviewCycleProgressVO progress,
        LocalDateTime lastReviewedAt,
        LocalDateTime masteredAt
) {
}
