package com.jt.learning.config;

import com.jt.learning.service.ai.client.GoogleAiAnswerScoringClient;
import com.jt.learning.service.ai.client.GoogleAiJapaneseCorrectionClient;
import com.jt.learning.service.ai.client.AiProviderHttpResponse;
import com.jt.learning.service.ai.client.GoogleAiQuestionClient;
import com.jt.learning.service.ai.client.GoogleAiEmbeddingClient;
import com.jt.learning.service.ai.client.MockAiAnswerScoringClient;
import com.jt.learning.service.ai.client.MockAiJapaneseCorrectionClient;
import com.jt.learning.service.ai.client.MockAiQuestionClient;
import com.jt.learning.service.ai.client.MockAiEmbeddingClient;
import com.jt.learning.service.ai.client.GoogleAiReviewQuestionClient;
import com.jt.learning.service.ai.client.GoogleAiReviewScoringClient;
import com.jt.learning.service.ai.client.MockAiReviewQuestionClient;
import com.jt.learning.service.ai.client.MockAiReviewScoringClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientConfigTest {

    private final AiClientConfig config = new AiClientConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aiQuestionClientShouldUseMockProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("mock");

        Object client = config.aiQuestionClient(
                properties,
                objectMapper,
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}")
        );

        assertThat(client).isInstanceOf(MockAiQuestionClient.class);
    }

    @Test
    void aiQuestionClientShouldUseGoogleProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("google");

        Object client = config.aiQuestionClient(
                properties,
                objectMapper,
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}")
        );

        assertThat(client).isInstanceOf(GoogleAiQuestionClient.class);
    }

    @Test
    void aiQuestionClientShouldRejectUnsupportedProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");

        assertThatThrownBy(() -> config.aiQuestionClient(
                properties,
                objectMapper,
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的 AI provider");
    }

    @Test
    void aiEmbeddingClientShouldUseConfiguredProvider() {
        var httpClient = (com.jt.learning.service.ai.client.AiProviderHttpClient)
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}");
        AiProperties mockProperties = new AiProperties();
        mockProperties.setProvider("mock");
        AiProperties googleProperties = new AiProperties();
        googleProperties.setProvider("google");

        assertThat(config.aiEmbeddingClient(mockProperties, objectMapper, httpClient))
                .isInstanceOf(MockAiEmbeddingClient.class);
        assertThat(config.aiEmbeddingClient(googleProperties, objectMapper, httpClient))
                .isInstanceOf(GoogleAiEmbeddingClient.class);
    }

    @Test
    void aiAnswerScoringClientShouldUseMockProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("mock");

        Object client = config.aiAnswerScoringClient(
                properties,
                objectMapper,
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}")
        );

        assertThat(client).isInstanceOf(MockAiAnswerScoringClient.class);
    }

    @Test
    void aiAnswerScoringClientShouldUseGoogleProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("google");

        Object client = config.aiAnswerScoringClient(
                properties,
                objectMapper,
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}")
        );

        assertThat(client).isInstanceOf(GoogleAiAnswerScoringClient.class);
    }

    @Test
    void japaneseCorrectionClientShouldUseConfiguredProvider() {
        var httpClient = (com.jt.learning.service.ai.client.AiProviderHttpClient)
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}");
        AiProperties mockProperties = new AiProperties();
        mockProperties.setProvider("mock");
        AiProperties googleProperties = new AiProperties();
        googleProperties.setProvider("google");

        assertThat(config.aiJapaneseCorrectionClient(mockProperties, objectMapper, httpClient))
                .isInstanceOf(MockAiJapaneseCorrectionClient.class);
        assertThat(config.aiJapaneseCorrectionClient(googleProperties, objectMapper, httpClient))
                .isInstanceOf(GoogleAiJapaneseCorrectionClient.class);
    }

    @Test
    void reviewClientsShouldUseMockProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("mock");
        var httpClient = (com.jt.learning.service.ai.client.AiProviderHttpClient)
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}");

        assertThat(config.aiReviewScoringClient(properties, objectMapper, httpClient))
                .isInstanceOf(MockAiReviewScoringClient.class);
        assertThat(config.aiReviewQuestionClient(properties, objectMapper, httpClient))
                .isInstanceOf(MockAiReviewQuestionClient.class);
    }

    @Test
    void reviewClientsShouldUseGoogleProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("google");
        var httpClient = (com.jt.learning.service.ai.client.AiProviderHttpClient)
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}");

        assertThat(config.aiReviewScoringClient(properties, objectMapper, httpClient))
                .isInstanceOf(GoogleAiReviewScoringClient.class);
        assertThat(config.aiReviewQuestionClient(properties, objectMapper, httpClient))
                .isInstanceOf(GoogleAiReviewQuestionClient.class);
    }
}
