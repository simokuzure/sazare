package com.jt.learning.service.ai.client;

import com.jt.learning.config.AiProperties;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class GoogleAiClientErrorHandlingTest {

    private static final String ERROR_RESPONSE = """
            {
              "error": {
                "message": "Quota exceeded for this model.",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiQuestionPrompt prompt = new AiQuestionPrompt("system", "user");

    @Test
    void allGoogleClientsShouldIncludeProviderErrorDetailsForNonSuccessfulStatus() {
        AiProviderHttpClient httpClient = responseClient(429, ERROR_RESPONSE);
        AiProperties.Google properties = properties();

        assertThat(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiQuestionClient(properties, objectMapper, httpClient).generateQuestions(
                        prompt,
                        new AiQuestionGenerationRequest(null, null, null, null, null, null, null),
                        List.of(),
                        List.of()
                )
        )).hasMessage("Google AI 服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded for this model.");

        assertThat(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiAnswerScoringClient(properties, objectMapper, httpClient)
                        .scoreAnswer(prompt, null, null, List.of(), List.of())
        )).hasMessage("Google AI 服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded for this model.");

        assertThat(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiEmbeddingClient(properties, objectMapper, httpClient).embed("测试内容")
        )).hasMessage("Google AI 嵌入服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded for this model.");

        assertThat(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiJapaneseCorrectionClient(properties, objectMapper, httpClient)
                        .correct(prompt, null)
        )).hasMessage("Google AI 日语纠错服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded for this model.");

        assertThat(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiReviewScoringClient(properties, objectMapper, httpClient).scoreAnswer(prompt)
        )).hasMessage("Google AI 服务返回异常: HTTP 429 RESOURCE_EXHAUSTED - Quota exceeded for this model.");
    }

    @Test
    void allGoogleClientsShouldRetainJsonParsingCause() {
        AiProviderHttpClient httpClient = responseClient(200, "{invalid-json");
        AiProperties.Google properties = properties();

        assertJacksonCause(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiQuestionClient(properties, objectMapper, httpClient).generateQuestions(
                        prompt,
                        new AiQuestionGenerationRequest(null, null, null, null, null, null, null),
                        List.of(),
                        List.of()
                )
        ));
        assertJacksonCause(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiAnswerScoringClient(properties, objectMapper, httpClient)
                        .scoreAnswer(prompt, null, null, List.of(), List.of())
        ));
        assertJacksonCause(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiEmbeddingClient(properties, objectMapper, httpClient).embed("测试内容")
        ));
        assertJacksonCause(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiJapaneseCorrectionClient(properties, objectMapper, httpClient)
                        .correct(prompt, null)
        ));
        assertJacksonCause(catchThrowableOfType(BusinessException.class,
                () -> new GoogleAiReviewQuestionClient(properties, objectMapper, httpClient).generateQuestion(prompt)
        ));
    }

    private void assertJacksonCause(BusinessException exception) {
        assertThat(exception.getCause()).isInstanceOf(JacksonException.class);
    }

    private AiProviderHttpClient responseClient(int statusCode, String responseBody) {
        return (uri, headers, body) -> new AiProviderHttpResponse(statusCode, responseBody);
    }

    private AiProperties.Google properties() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://example.test/v1beta");
        properties.setModel("gemini-test");
        properties.setEmbeddingModel("gemini-embedding-test");
        return properties;
    }
}
