package com.jt.learning.service.impl;

public record AiProviderHttpResponse(
        int statusCode,
        String body
) {
}
