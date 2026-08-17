package com.jt.learning.vo;

import java.time.LocalDateTime;

public record ReviewCardCreatedVO(
        Long id,
        String name,
        String status,
        LocalDateTime dueAt
) {
}
