package com.jt.learning.config;

import com.jt.learning.service.impl.GoogleAiAnswerScoringClient;
import com.jt.learning.service.impl.AiProviderHttpResponse;
import com.jt.learning.service.impl.GoogleAiQuestionClient;
import com.jt.learning.service.impl.MockAiAnswerScoringClient;
import com.jt.learning.service.impl.MockAiQuestionClient;
import com.jt.learning.service.impl.GoogleAiReviewQuestionClient;
import com.jt.learning.service.impl.GoogleAiReviewScoringClient;
import com.jt.learning.service.impl.MockAiReviewQuestionClient;
import com.jt.learning.service.impl.MockAiReviewScoringClient;
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
    void reviewClientsShouldUseMockProvider() {
        AiProperties properties = new AiProperties();
        properties.setProvider("mock");
        var httpClient = (com.jt.learning.service.impl.AiProviderHttpClient)
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
        var httpClient = (com.jt.learning.service.impl.AiProviderHttpClient)
                (uri, headers, body) -> new AiProviderHttpResponse(200, "{}");

        assertThat(config.aiReviewScoringClient(properties, objectMapper, httpClient))
                .isInstanceOf(GoogleAiReviewScoringClient.class);
        assertThat(config.aiReviewQuestionClient(properties, objectMapper, httpClient))
                .isInstanceOf(GoogleAiReviewQuestionClient.class);
    }
}
