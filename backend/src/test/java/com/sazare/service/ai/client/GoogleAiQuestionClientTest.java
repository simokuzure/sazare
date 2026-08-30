package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.exception.BusinessException;
import com.sazare.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAiQuestionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generateQuestionsShouldCallGoogleGenerateContentApi() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(200, """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "text": "{\\"questions\\":[]}"
                          }
                        ]
                      }
                    }
                  ]
                }
                """));
        GoogleAiQuestionClient client = new GoogleAiQuestionClient(properties(), objectMapper, httpClient);

        String text = client.generateQuestions(
                new AiQuestionPrompt("system prompt", "user prompt"),
                new AiQuestionGenerationRequest(null, null, null, null, null, null, null),
                List.of(),
                List.of()
        );

        assertThat(text).isEqualTo("{\"questions\":[]}");
        assertThat(httpClient.uri.toString())
                .isEqualTo("https://example.test/v1beta/models/gemini-3.6-flash:generateContent");
        assertThat(httpClient.headers)
                .containsEntry("Content-Type", "application/json")
                .containsEntry("x-goog-api-key", "test-key");

        JsonNode requestBody = objectMapper.readTree(httpClient.body);
        assertThat(requestBody.get("systemInstruction").get("parts").get(0).get("text").asString())
                .isEqualTo("system prompt");
        assertThat(requestBody.get("contents").get(0).get("parts").get(0).get("text").asString())
                .isEqualTo("user prompt");
        assertThat(requestBody.get("generationConfig").get("responseMimeType").asString())
                .isEqualTo("application/json");
        assertThat(requestBody.get("generationConfig").get("temperature")).isNull();
        assertThat(requestBody.get("generationConfig").get("topP")).isNull();
    }

    @Test
    void generateArticleShouldUseArticleSamplingConfiguration() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(200, """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [{"text": "{\\"blueprint\\":{},\\"article\\":{}}"}]
                      }
                    }
                  ]
                }
                """));
        GoogleAiQuestionClient client = new GoogleAiQuestionClient(properties(), objectMapper, httpClient);

        client.generateArticle(
                new AiQuestionPrompt("system prompt", "user prompt"),
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null),
                "123e4567-e89b-12d3-a456-426614174000"
        );

        JsonNode generationConfig = objectMapper.readTree(httpClient.body).get("generationConfig");
        assertThat(generationConfig.get("responseMimeType").asString()).isEqualTo("application/json");
        assertThat(generationConfig.get("temperature").asDouble()).isEqualTo(1.1d);
        assertThat(generationConfig.get("topP").asDouble()).isEqualTo(0.98d);
        assertThat(generationConfig.get("topK")).isNull();
        assertThat(generationConfig.get("seed")).isNull();
    }

    @Test
    void generateArticleShouldRejectInvalidSamplingConfiguration() {
        AiProperties.Google properties = properties();
        properties.setArticleTemperature(2.1d);
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(200, "{}"));
        GoogleAiQuestionClient client = new GoogleAiQuestionClient(properties, objectMapper, httpClient);

        assertThatThrownBy(() -> client.generateArticle(
                new AiQuestionPrompt("system", "user"),
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null),
                "123e4567-e89b-12d3-a456-426614174000"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("temperature");
        assertThat(httpClient.called).isFalse();
    }

    @Test
    void generateQuestionsShouldRejectMissingApiKey() {
        AiProperties.Google properties = properties();
        properties.setApiKey("");
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(200, "{}"));
        GoogleAiQuestionClient client = new GoogleAiQuestionClient(properties, objectMapper, httpClient);

        assertThatThrownBy(() -> client.generateQuestions(
                new AiQuestionPrompt("system", "user"),
                new AiQuestionGenerationRequest(null, null, null, null, null, null, null),
                List.of(),
                List.of()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("API key 未配置");
        assertThat(httpClient.called).isFalse();
    }

    @Test
    void generateQuestionsShouldRejectNonSuccessStatusCode() {
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(429, """
                {
                  "error": {
                    "code": 429,
                    "message": "Quota exceeded for this model.",
                    "status": "RESOURCE_EXHAUSTED"
                  }
                }
                """));
        GoogleAiQuestionClient client = new GoogleAiQuestionClient(properties(), objectMapper, httpClient);

        assertThatThrownBy(() -> client.generateQuestions(
                new AiQuestionPrompt("system", "user"),
                new AiQuestionGenerationRequest(null, null, null, null, null, null, null),
                List.of(),
                List.of()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Google AI 服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded for this model.");
    }

    @Test
    void generateQuestionsShouldRejectResponseWithoutText() {
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(200, """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": []
                      }
                    }
                  ]
                }
                """));
        GoogleAiQuestionClient client = new GoogleAiQuestionClient(properties(), objectMapper, httpClient);

        assertThatThrownBy(() -> client.generateQuestions(
                new AiQuestionPrompt("system", "user"),
                new AiQuestionGenerationRequest(null, null, null, null, null, null, null),
                List.of(),
                List.of()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少文本内容");
    }

    private AiProperties.Google properties() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://example.test/v1beta");
        properties.setModel("gemini-3.6-flash");
        return properties;
    }

    private static class CapturingHttpClient implements AiProviderHttpClient {

        private final AiProviderHttpResponse response;
        private boolean called;
        private URI uri;
        private Map<String, String> headers;
        private String body;

        private CapturingHttpClient(AiProviderHttpResponse response) {
            this.response = response;
        }

        @Override
        public AiProviderHttpResponse postJson(URI uri, Map<String, String> headers, String body) {
            called = true;
            this.uri = uri;
            this.headers = headers;
            this.body = body;
            return response;
        }
    }
}
