package com.sazare.dto;

import java.time.LocalDateTime;

public record ReviewSchedulingEvent(Integer quality, LocalDateTime reviewedAt) {
}
