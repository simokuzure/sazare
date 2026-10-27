package com.sazare.config;

import com.sazare.service.ai.AiAnswerScoringClient;
import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.AiEmbeddingClient;
import com.sazare.service.ai.AiJapaneseCorrectionClient;
import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiReviewQuestionClient;
import com.sazare.service.ai.AiReviewScoringClient;
import com.sazare.service.ai.AiSpeechTranscriptionClient;
import com.sazare.service.ai.client.AiProviderHttpClient;
import com.sazare.service.ai.client.GoogleAiEmbeddingClient;
import com.sazare.service.ai.client.GoogleAiSpeechTranscriptionClient;
import com.sazare.service.ai.client.MockAiChatImportClient;
import com.sazare.service.ai.client.MockAiEmbeddingClient;
import com.sazare.service.ai.client.MockAiSpeechTranscriptionClient;
import com.sazare.service.ai.client.OpenAiAnswerScoringClient;
import com.sazare.service.ai.client.OpenAiChatImportClient;
import com.sazare.service.ai.client.OpenAiJapaneseCorrectionClient;
import com.sazare.service.ai.client.OpenAiQuestionClient;
import com.sazare.service.ai.client.OpenAiReviewQuestionClient;
import com.sazare.service.ai.client.OpenAiReviewScoringClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestAiClientConfig.class, MapperConfig.class);

    @Test
    void routesOnlyTextToOpenAiAndBindsProperties() {
        contextRunner.withPropertyValues(
                "ai.provider= OpenAI ",
                "ai.providers.openai.api-key=test-key",
                "ai.providers.openai.model=configured-model",
                "ai.providers.openai.base-url=https://example.invalid/v1",
                "ai.request-timeout=75s"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiQuestionClient.class)).isInstanceOf(OpenAiQuestionClient.class);
            assertThat(context.getBean(AiAnswerScoringClient.class)).isInstanceOf(OpenAiAnswerScoringClient.class);
            assertThat(context.getBean(AiJapaneseCorrectionClient.class)).isInstanceOf(OpenAiJapaneseCorrectionClient.class);
            assertThat(context.getBean(AiReviewQuestionClient.class)).isInstanceOf(OpenAiReviewQuestionClient.class);
            assertThat(context.getBean(AiReviewScoringClient.class)).isInstanceOf(OpenAiReviewScoringClient.class);
            assertThat(context.getBean(AiChatImportClient.class)).isInstanceOf(OpenAiChatImportClient.class);
            assertThat(context.getBean(AiEmbeddingClient.class)).isInstanceOf(GoogleAiEmbeddingClient.class);
            assertThat(context.getBean(AiSpeechTranscriptionClient.class)).isInstanceOf(GoogleAiSpeechTranscriptionClient.class);
            AiProperties properties = context.getBean(AiProperties.class);
            assertThat(properties.getProviders().getOpenai().getApiKey()).isEqualTo("test-key");
            assertThat(properties.getProviders().getOpenai().getModel()).isEqualTo("configured-model");
            assertThat(properties.getProviders().getOpenai().getBaseUrl()).isEqualTo("https://example.invalid/v1");
            assertThat(properties.getRequestTimeout()).hasSeconds(75);
        });
    }

    @Test
    void retainsMockForSpeechEmbeddingAndChatImport() {
        contextRunner.withPropertyValues("ai.provider=mock").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AiEmbeddingClient.class)).isInstanceOf(MockAiEmbeddingClient.class);
            assertThat(context.getBean(AiSpeechTranscriptionClient.class)).isInstanceOf(MockAiSpeechTranscriptionClient.class);
            assertThat(context.getBean(AiChatImportClient.class)).isInstanceOf(MockAiChatImportClient.class);
        });
    }

    @Test
    void defaultsToGoogleAndProvidesOpenAiDefaultsWithoutRequiringKeyAtStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            AiProperties properties = context.getBean(AiProperties.class);
            assertThat(properties.getProvider()).isEqualTo("google");
            assertThat(properties.getProviders().getOpenai().getModel()).isEqualTo("gpt-6.1-sol");
            assertThat(properties.getProviders().getOpenai().getBaseUrl()).isEqualTo("https://api.openai.com/v1");
            assertThat(properties.getProviders().getOpenai().getApiKey()).isEmpty();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiProperties.class)
    static class TestAiClientConfig extends AiClientConfig {
        @Bean
        @Override
        public AiProviderHttpClient aiProviderHttpClient(AiProperties properties) {
            return (uri, headers, body) -> {
                throw new AssertionError("配置路由测试不应发送 HTTP 请求");
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MapperConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
