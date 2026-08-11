package com.jt.learning.service.ai.client;

public record AiProviderHttpResponse(
        int statusCode,
        String body
) {
}
