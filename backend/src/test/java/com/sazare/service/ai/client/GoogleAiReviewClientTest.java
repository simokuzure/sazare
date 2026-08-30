package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAiReviewClientTest {

    @Test
    void scoringAndQuestionClientsShouldExtractProviderText() {
        AiProperties.Google properties = properties();
        AiProviderHttpClient httpClient = (uri, headers, body) -> new AiProviderHttpResponse(
                200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"review\\\":{}}\"}]}}]}"
        );
        AiQuestionPrompt prompt = new AiQuestionPrompt("system", "user");

        assertThat(new GoogleAiReviewScoringClient(properties, new ObjectMapper(), httpClient).scoreAnswer(prompt))
                .isEqualTo("{\"review\":{}}");
        assertThat(new GoogleAiReviewQuestionClient(properties, new ObjectMapper(), httpClient).generateQuestion(prompt))
                .isEqualTo("{\"review\":{}}");
    }

    @Test
    void shouldRejectNonSuccessfulProviderStatus() {
        AiProviderHttpClient httpClient = (uri, headers, body) -> new AiProviderHttpResponse(500, "{}");

        assertThatThrownBy(() -> new GoogleAiReviewScoringClient(
                properties(), new ObjectMapper(), httpClient
        ).scoreAnswer(new AiQuestionPrompt("system", "user")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务返回异常");
    }

    private AiProperties.Google properties() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("test-key");
        properties.setModel("test-model");
        properties.setBaseUrl("https://example.com/v1beta");
        return properties;
    }
}
