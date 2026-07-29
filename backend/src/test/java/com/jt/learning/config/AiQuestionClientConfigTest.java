package com.jt.learning.config;

import com.jt.learning.service.impl.AiProviderHttpResponse;
import com.jt.learning.service.impl.GoogleAiQuestionClient;
import com.jt.learning.service.impl.MockAiQuestionClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiQuestionClientConfigTest {

    private final AiQuestionClientConfig config = new AiQuestionClientConfig();
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
}
