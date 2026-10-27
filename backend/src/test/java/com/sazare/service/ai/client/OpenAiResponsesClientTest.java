package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiResponsesClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiProperties.OpenAi properties = new AiProperties.OpenAi();
    private final RecordingHttpClient http = new RecordingHttpClient();
    private final AiQuestionPrompt prompt = new AiQuestionPrompt("只返回 JSON，保留日语。", "日本語を確認してください。");

    @Test
    void sendsResponsesRequestWithConfiguredModelAndOriginalPrompts() {
        properties.setApiKey(" test-key ");
        properties.setModel(" configured-model ");
        properties.setBaseUrl("https://example.invalid/v1/// ");
        http.response = completed("{\"日本語\":true}");

        assertThat(client().generate(prompt)).isEqualTo("{\"日本語\":true}");
        assertThat(http.uri).isEqualTo(URI.create("https://example.invalid/v1/responses"));
        assertThat(http.headers).containsEntry("Authorization", "Bearer test-key")
                .containsEntry("Content-Type", "application/json");
        JsonNode request = mapper.readTree(http.body);
        assertThat(request.path("model").asString()).isEqualTo("configured-model");
        assertThat(request.has("instructions")).isFalse();
        JsonNode messages = request.path("input");
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).path("role").asString()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asString()).isEqualTo(prompt.systemPrompt());
        assertThat(messages.get(1).path("role").asString()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asString()).isEqualTo(prompt.userPrompt());
        assertThat(request.path("text").path("format").path("type").asString()).isEqualTo("json_object");
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.has("temperature")).isFalse();
        assertThat(request.has("top_p")).isFalse();
        assertThat(request.has("reasoning")).isFalse();
        assertThat(http.calls).isEqualTo(1);
    }

    @Test
    void combinesAssistantTextAndIgnoresReasoningAndOtherRoles() {
        properties.setApiKey("test-key");
        http.response = response(Map.of("status", "completed", "output", List.of(
                Map.of("type", "reasoning", "summary", List.of(Map.of("type", "summary_text", "text", "private"))),
                Map.of("type", "message", "role", "user", "content", List.of(text("ignore"))),
                message(List.of(text("{\"日本語\":"), text("true}")))
        )));

        assertThat(client().generate(prompt)).isEqualTo("{\"日本語\":true}");
        assertThat(http.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingKeyWithoutSendingRequest(String value) {
        properties.setApiKey(value);
        assertThatThrownBy(() -> client().generate(prompt)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("API key 未配置");
        assertThat(http.calls).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingModelWithoutSendingRequest(String value) {
        properties.setApiKey("test-key");
        properties.setModel(value);
        assertThatThrownBy(() -> client().generate(prompt)).hasMessageContaining("model 未配置");
        assertThat(http.calls).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsMissingBaseUrlWithoutSendingRequest(String value) {
        properties.setApiKey("test-key");
        properties.setBaseUrl(value);
        assertThatThrownBy(() -> client().generate(prompt)).hasMessageContaining("baseUrl 未配置");
        assertThat(http.calls).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 502, 503})
    void preservesHttpStatusWithoutRetryOrLeakingResponse(int status) {
        properties.setApiKey("test-key");
        http.response = new AiProviderHttpResponse(status, "private-provider-response test-key");

        assertThatThrownBy(() -> client().generate(prompt)).isInstanceOf(AiProviderHttpException.class)
                .satisfies(error -> assertThat(((AiProviderHttpException) error).getStatusCode()).isEqualTo(status))
                .hasMessageContaining("OpenAI")
                .hasMessageNotContaining("private-provider-response")
                .hasMessageNotContaining("test-key");
        assertThat(http.calls).isEqualTo(1);
    }

    @Test
    void reportsSafeErrorIdentifiersWithoutEchoingProviderMessageOrInput() {
        properties.setApiKey("test-key");
        http.response = response(Map.of("error", Map.of(
                "type", "invalid_request_error", "code", "unsupported_value", "param", "text.format.type",
                "message", "private-provider-response test-key " + prompt.userPrompt()
        )));
        http.response = new AiProviderHttpResponse(400, http.response.body());

        assertThatThrownBy(() -> client().generate(prompt)).isInstanceOf(AiProviderHttpException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("type=invalid_request_error")
                .hasMessageContaining("code=unsupported_value")
                .hasMessageContaining("param=text.format.type")
                .hasMessageNotContaining("private-provider-response")
                .hasMessageNotContaining("test-key")
                .hasMessageNotContaining(prompt.userPrompt())
                .hasNoCause();
        assertThat(http.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private provider text", "private\nlog", "test-key", "secret-token-value",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void omitsUnsafeErrorIdentifiers(String identifier) {
        properties.setApiKey("test-key");
        http.response = new AiProviderHttpResponse(400, mapper.writeValueAsString(Map.of(
                "error", Map.of("type", identifier, "code", identifier, "param", identifier)
        )));
        assertThatThrownBy(() -> client().generate(prompt)).hasMessage("OpenAI 请求参数不合法（HTTP 400）");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-json", "null", "[]", "{}", "{\"error\":null}",
            "{\"error\":{\"type\":123,\"code\":[],\"param\":{}}}"})
    void malformedErrorBodyDoesNotHideHttpStatus(String body) {
        properties.setApiKey("test-key");
        http.response = new AiProviderHttpResponse(400, body);
        assertThatThrownBy(() -> client().generate(prompt)).isInstanceOf(AiProviderHttpException.class)
                .hasMessage("OpenAI 请求参数不合法（HTTP 400）").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"incomplete", "failed", "in_progress", "cancelled", "queued", ""})
    void rejectsUnfinishedResponseEvenIfTextIsValidJson(String status) {
        properties.setApiKey("test-key");
        http.response = response(Map.of("status", status, "output", List.of(message(List.of(text("{}"))))));
        assertThatThrownBy(() -> client().generate(prompt)).hasMessageContaining("未完整完成");
        assertThat(http.calls).isEqualTo(1);
    }

    @Test
    void rejectsUnfinishedMessage() {
        properties.setApiKey("test-key");
        http.response = response(Map.of("status", "completed", "output", List.of(Map.of(
                "type", "message", "role", "assistant", "status", "incomplete", "content", List.of(text("{}"))
        ))));
        assertThatThrownBy(() -> client().generate(prompt)).hasMessageContaining("未完整完成");
    }

    @Test
    void rejectsRefusalEvenWhenOtherPartsContainJson() {
        properties.setApiKey("test-key");
        http.response = response(Map.of("status", "completed", "output", List.of(message(List.of(
                text("{}"), Map.of("type", "refusal", "refusal", "private-refusal")
        )))));
        assertThatThrownBy(() -> client().generate(prompt)).hasMessageContaining("无法处理")
                .hasMessageNotContaining("private-refusal");
        assertThat(http.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-json", "null", "[]", "{}",
            "{\"status\":\"completed\"}", "{\"status\":\"completed\",\"output\":[]}"})
    void rejectsMissingOrMalformedResponse(String body) {
        properties.setApiKey("test-key");
        http.response = new AiProviderHttpResponse(200, body);
        assertThatThrownBy(() -> client().generate(prompt)).isInstanceOf(BusinessException.class);
        assertThat(http.calls).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "not-json", "{", "null", "[]", "true", "{} trailing", "{} {}"})
    void rejectsMissingOrInvalidJsonObject(String output) {
        properties.setApiKey("test-key");
        http.response = completed(output);
        assertThatThrownBy(() -> client().generate(prompt)).isInstanceOf(BusinessException.class);
        assertThat(http.calls).isEqualTo(1);
    }

    private OpenAiResponsesClient client() {
        return new OpenAiResponsesClient(properties, mapper, http);
    }

    private AiProviderHttpResponse completed(String output) {
        return response(Map.of("status", "completed", "output", List.of(message(List.of(text(output))))));
    }

    private AiProviderHttpResponse response(Map<String, Object> body) {
        return new AiProviderHttpResponse(200, mapper.writeValueAsString(body));
    }

    private Map<String, Object> message(List<Map<String, Object>> content) {
        return Map.of("type", "message", "role", "assistant", "status", "completed", "content", content);
    }

    private Map<String, Object> text(String value) {
        return Map.of("type", "output_text", "text", value);
    }

    private static class RecordingHttpClient implements AiProviderHttpClient {
        private AiProviderHttpResponse response;
        private URI uri;
        private Map<String, String> headers;
        private String body;
        private int calls;

        @Override
        public AiProviderHttpResponse postJson(URI uri, Map<String, String> headers, String body) {
            calls++;
            this.uri = uri;
            this.headers = headers;
            this.body = body;
            return response;
        }
    }
}
