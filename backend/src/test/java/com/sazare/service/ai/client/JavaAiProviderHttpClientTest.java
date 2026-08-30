package com.sazare.service.ai.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
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

    @Test
    @SuppressWarnings("unchecked")
    void successfulRequestShouldLogEndpointStatusAndDurationWithoutApiKey(CapturedOutput output) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        JavaAiProviderHttpClient client = new JavaAiProviderHttpClient(httpClient, Duration.ofSeconds(10));

        client.postJson(
                URI.create("https://example.com/models/test:generateContent?key=secret-in-query"),
                Map.of("x-goog-api-key", "secret-api-key"),
                "{\"prompt\":\"secret-prompt\"}"
        );

        assertThat(output.getOut())
                .contains("AI 服务调用开始")
                .contains("AI 服务调用成功")
                .contains("endpoint=https://example.com/models/test:generateContent")
                .contains("status=200")
                .contains("durationMs=")
                .doesNotContain("secret-in-query", "secret-api-key", "secret-prompt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ioFailureShouldLogAndRetainOriginalCause(CapturedOutput output) throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        IOException cause = new IOException("connection failed");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(cause);
        JavaAiProviderHttpClient client = new JavaAiProviderHttpClient(httpClient, Duration.ofSeconds(10));

        assertThatThrownBy(() -> client.postJson(
                URI.create("https://example.com/models/test:generateContent"),
                Map.of(),
                "{}"
        ))
                .isInstanceOf(com.sazare.exception.BusinessException.class)
                .hasMessage("AI 服务请求失败")
                .hasCause(cause);
        assertThat(output.getOut())
                .contains("AI 服务调用失败")
                .contains("durationMs=")
                .contains("connection failed");
    }
}
