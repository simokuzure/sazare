package com.jt.learning.service.impl;

import com.jt.learning.config.AiProperties;
import com.jt.learning.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAiEmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embedShouldCallGoogleEmbedContentApi() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient(new AiProviderHttpResponse(200, validResponse()));
        GoogleAiEmbeddingClient client = new GoogleAiEmbeddingClient(properties(), objectMapper, httpClient);

        assertThat(client.embed("中文题目")).hasSize(768).allMatch(value -> value == 0.1f);
        assertThat(httpClient.uri.toString())
                .isEqualTo("https://example.test/v1beta/models/gemini-embedding-001:embedContent");
        assertThat(httpClient.headers).containsEntry("x-goog-api-key", "test-key");

        JsonNode requestBody = objectMapper.readTree(httpClient.body);
        assertThat(requestBody.get("content").get("parts").get(0).get("text").asString()).isEqualTo("中文题目");
        assertThat(requestBody.get("outputDimensionality").asInt()).isEqualTo(768);
        assertThat(requestBody.get("embedContentConfig").get("outputDimensionality").asInt()).isEqualTo(768);
    }

    @Test
    void embedShouldRejectIncorrectDimension() {
        GoogleAiEmbeddingClient client = new GoogleAiEmbeddingClient(
                properties(),
                objectMapper,
                new CapturingHttpClient(new AiProviderHttpResponse(200, "{\"embedding\":{\"values\":[0.1]}}"))
        );

        assertThatThrownBy(() -> client.embed("中文题目"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("嵌入维度不正确");
    }

    private AiProperties.Google properties() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://example.test/v1beta");
        properties.setEmbeddingModel("gemini-embedding-001");
        return properties;
    }

    private String validResponse() {
        return "{\"embedding\":{\"values\":[" + "0.1,".repeat(767) + "0.1]}}";
    }

    private static class CapturingHttpClient implements AiProviderHttpClient {

        private final AiProviderHttpResponse response;
        private URI uri;
        private Map<String, String> headers;
        private String body;

        private CapturingHttpClient(AiProviderHttpResponse response) {
            this.response = response;
        }

        @Override
        public AiProviderHttpResponse postJson(URI uri, Map<String, String> headers, String body) {
            this.uri = uri;
            this.headers = headers;
            this.body = body;
            return response;
        }
    }
}
