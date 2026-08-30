package com.sazare.service.ai.client;

public record AiProviderHttpResponse(
        int statusCode,
        String body
) {
}
