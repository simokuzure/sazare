package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleGenerateContentClientTest {

    private static final AiProviderHttpResponse SUCCESS = new AiProviderHttpResponse(200,
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}");
    private static final AiProviderHttpResponse QUOTA = new AiProviderHttpResponse(429,
            "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"Quota exceeded\"}}");
    private final AiProperties.Google properties = new AiProperties.Google();
    private final List<URI> calls = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<Map<String, String>> headers = new ArrayList<>();

    @Test
    void shouldFallbackFromConfiguredModelAndPreserveRequest() {
        GoogleGenerateContentClient client = client(" models/gemini-3.8-flash ", QUOTA, QUOTA, SUCCESS);

        assertThat(generate(client)).isEqualTo("ok");
        assertModels("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.7-flash");
        assertThat(bodies).hasSize(3).containsOnly(bodies.getFirst());
        assertThat(headers).hasSize(3).containsOnly(headers.getFirst());
        assertThat(properties.getModel()).isEqualTo(" models/gemini-3.8-flash ");
    }

    @Test
    void shouldStopAfterFallbackSuccess() {
        assertThat(generate(client("gemini-3.6-flash", QUOTA, SUCCESS))).isEqualTo("ok");
        assertModels("gemini-3.6-flash", "gemini-3.7-flash");
    }

    @Test
    void shouldNotRetrySuccess() {
        assertThat(generate(client("gemini-3.7-flash", SUCCESS))).isEqualTo("ok");
        assertModels("gemini-3.7-flash");
    }

    @Test
    void shouldTryEachModelOnlyOnceAndPreserveFinalError() {
        GoogleGenerateContentClient client = client("gemini-3.7-flash", QUOTA, QUOTA, QUOTA);
        assertThatThrownBy(() -> generate(client))
                .isInstanceOf(AiProviderHttpException.class)
                .hasMessage("Google AI 日语纠错服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded");
        assertModels("gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.8-flash");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 500, 503})
    void shouldStopOnNonQuotaErrorEvenDuringFallback(int status) {
        GoogleGenerateContentClient client = client("gemini-3.8-flash", QUOTA,
                new AiProviderHttpResponse(status, "{}"));
        assertThatThrownBy(() -> generate(client)).isInstanceOf(AiProviderHttpException.class)
                .satisfies(error -> assertThat(((AiProviderHttpException) error).getStatusCode()).isEqualTo(status));
        assertModels("gemini-3.8-flash", "gemini-3.6-flash");
    }

    @Test
    void shouldNotFallbackForOtherModels() {
        GoogleGenerateContentClient client = client("custom-model", QUOTA);
        assertThatThrownBy(() -> generate(client)).isInstanceOf(AiProviderHttpException.class);
        assertModels("custom-model");
    }

    @Test
    void shouldNotRetryInvalidSuccessfulResponse() {
        GoogleGenerateContentClient client = client("gemini-3.8-flash", new AiProviderHttpResponse(200, "{}"));
        assertThatThrownBy(() -> generate(client)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少候选结果");
        assertModels("gemini-3.8-flash");
    }

    private GoogleGenerateContentClient client(String model, AiProviderHttpResponse... responses) {
        properties.setModel(model);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://example.test/v1beta/");
        return new GoogleGenerateContentClient(properties, new ObjectMapper(), (uri, requestHeaders, body) -> {
            calls.add(uri);
            bodies.add(body);
            headers.add(requestHeaders);
            return responses[calls.size() - 1];
        });
    }

    private String generate(GoogleGenerateContentClient client) {
        return client.generate(new AiQuestionPrompt("system", "user"),
                Map.of("responseMimeType", "application/json", "temperature", 1.1d), "日语纠错");
    }

    private void assertModels(String... models) {
        assertThat(calls).extracting(URI::getPath).containsExactly(
                java.util.Arrays.stream(models)
                        .map(model -> "/v1beta/models/" + model + ":generateContent")
                        .toArray(String[]::new));
    }
}
