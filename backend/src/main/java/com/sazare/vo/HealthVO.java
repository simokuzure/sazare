package com.sazare.vo;

import java.time.OffsetDateTime;

public record HealthVO(String status, String service, OffsetDateTime timestamp) {
}

