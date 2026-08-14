package com.jt.learning.service.ai.client;

import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class JavaAiProviderHttpClient implements AiProviderHttpClient {

    private static final Logger log = LoggerFactory.getLogger(JavaAiProviderHttpClient.class);
    private static final int ERROR_RESPONSE_LOG_LIMIT = 2_000;

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public JavaAiProviderHttpClient(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = validateRequestTimeout(requestTimeout);
    }

    @Override
    public AiProviderHttpResponse postJson(URI uri, Map<String, String> headers, String body) {
        try {
            HttpResponse<String> response = httpClient.send(
                    buildPostRequest(uri, headers, body),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "AI 服务调用失败: endpoint={}, status={}, responseBody={}",
                        endpointForLog(uri),
                        response.statusCode(),
                        summarizeResponseBody(response.body())
                );
            }
            return new AiProviderHttpResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("AI 服务调用被中断: endpoint={}", endpointForLog(uri), exception);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 服务请求被中断");
        } catch (IOException exception) {
            log.error("AI 服务调用失败: endpoint={}", endpointForLog(uri), exception);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 服务请求失败");
        }
    }

    HttpRequest buildPostRequest(URI uri, Map<String, String> headers, String body) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(requestBuilder::header);
        return requestBuilder.build();
    }

    private Duration validateRequestTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("AI 请求超时时间必须大于 0");
        }
        return timeout;
    }

    private String endpointForLog(URI uri) {
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port + uri.getPath();
    }

    private String summarizeResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= ERROR_RESPONSE_LOG_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, ERROR_RESPONSE_LOG_LIMIT) + "...(truncated)";
    }
}
