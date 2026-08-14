package com.jt.learning.service.ai.client;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaAiProviderHttpClientTest {

    @Test
    void postRequestShouldUseConfiguredTimeout() {
        Duration requestTimeout = Duration.ofSeconds(240);
        JavaAiProviderHttpClient client = new JavaAiProviderHttpClient(
                HttpClient.newHttpClient(),
                requestTimeout
        );

        var request = client.buildPostRequest(
                URI.create("https://example.com/generateContent"),
                Map.of("Content-Type", "application/json"),
                "{}"
        );

        assertThat(request.timeout()).contains(requestTimeout);
        assertThat(request.method()).isEqualTo("POST");
    }

    @Test
    void constructorShouldRejectNonPositiveTimeout() {
        HttpClient httpClient = HttpClient.newHttpClient();

        assertThatThrownBy(() -> new JavaAiProviderHttpClient(httpClient, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 请求超时时间必须大于 0");
    }
}
