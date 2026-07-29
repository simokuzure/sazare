package com.jt.learning.service.impl;

import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class JavaAiProviderHttpClient implements AiProviderHttpClient {

    private final HttpClient httpClient;

    public JavaAiProviderHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public AiProviderHttpResponse postJson(URI uri, Map<String, String> headers, String body) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(requestBuilder::header);

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return new AiProviderHttpResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 服务请求被中断");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 服务请求失败");
        }
    }
}
