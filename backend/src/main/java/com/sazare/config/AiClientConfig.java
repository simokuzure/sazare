package com.sazare.config;

import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiEmbeddingClient;
import com.sazare.service.ai.AiAnswerScoringClient;
import com.sazare.service.ai.AiJapaneseCorrectionClient;
import com.sazare.service.ai.AiReviewQuestionClient;
import com.sazare.service.ai.AiReviewScoringClient;
import com.sazare.service.ai.client.GoogleAiAnswerScoringClient;
import com.sazare.service.ai.client.GoogleAiJapaneseCorrectionClient;
import com.sazare.service.ai.client.GoogleAiQuestionClient;
import com.sazare.service.ai.client.GoogleAiEmbeddingClient;
import com.sazare.service.ai.client.GoogleAiReviewQuestionClient;
import com.sazare.service.ai.client.GoogleAiReviewScoringClient;
import com.sazare.service.ai.client.JavaAiProviderHttpClient;
import com.sazare.service.ai.client.MockAiAnswerScoringClient;
import com.sazare.service.ai.client.MockAiJapaneseCorrectionClient;
import com.sazare.service.ai.client.MockAiQuestionClient;
import com.sazare.service.ai.client.MockAiEmbeddingClient;
import com.sazare.service.ai.client.MockAiReviewQuestionClient;
import com.sazare.service.ai.client.MockAiReviewScoringClient;
import com.sazare.service.ai.client.AiProviderHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    @Bean
    public AiProviderHttpClient aiProviderHttpClient(AiProperties aiProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new JavaAiProviderHttpClient(httpClient, aiProperties.getRequestTimeout());
    }

    @Bean
    public AiQuestionClient aiQuestionClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiProviderHttpClient aiProviderHttpClient
    ) {
        return createClient(
                aiProperties,
                () -> new MockAiQuestionClient(objectMapper),
                () -> new GoogleAiQuestionClient(
                    aiProperties.getProviders().getGoogle(),
                    objectMapper,
                    aiProviderHttpClient
                )
        );
    }

    @Bean
    public AiEmbeddingClient aiEmbeddingClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiProviderHttpClient aiProviderHttpClient
    ) {
        return createClient(
                aiProperties,
                MockAiEmbeddingClient::new,
                () -> new GoogleAiEmbeddingClient(
                        aiProperties.getProviders().getGoogle(),
                        objectMapper,
                        aiProviderHttpClient
                )
        );
    }

    @Bean
    public AiAnswerScoringClient aiAnswerScoringClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiProviderHttpClient aiProviderHttpClient
    ) {
        return createClient(
                aiProperties,
                () -> new MockAiAnswerScoringClient(objectMapper),
                () -> new GoogleAiAnswerScoringClient(
                    aiProperties.getProviders().getGoogle(),
                    objectMapper,
                    aiProviderHttpClient
                )
        );
    }

    @Bean
    public AiJapaneseCorrectionClient aiJapaneseCorrectionClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiProviderHttpClient aiProviderHttpClient
    ) {
        return createClient(
                aiProperties,
                () -> new MockAiJapaneseCorrectionClient(objectMapper),
                () -> new GoogleAiJapaneseCorrectionClient(
                        aiProperties.getProviders().getGoogle(),
                        objectMapper,
                        aiProviderHttpClient
                )
        );
    }

    @Bean
    public AiReviewScoringClient aiReviewScoringClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiProviderHttpClient aiProviderHttpClient
    ) {
        return createClient(
                aiProperties,
                () -> new MockAiReviewScoringClient(objectMapper),
                () -> new GoogleAiReviewScoringClient(
                        aiProperties.getProviders().getGoogle(),
                        objectMapper,
                        aiProviderHttpClient
                )
        );
    }

    @Bean
    public AiReviewQuestionClient aiReviewQuestionClient(
            AiProperties aiProperties,
            ObjectMapper objectMapper,
            AiProviderHttpClient aiProviderHttpClient
    ) {
        return createClient(
                aiProperties,
                () -> new MockAiReviewQuestionClient(objectMapper),
                () -> new GoogleAiReviewQuestionClient(
                        aiProperties.getProviders().getGoogle(),
                        objectMapper,
                        aiProviderHttpClient
                )
        );
    }

    private <T> T createClient(
            AiProperties aiProperties,
            Supplier<T> mockClientFactory,
            Supplier<T> googleClientFactory
    ) {
        String provider = normalizeProvider(aiProperties.getProvider());
        return switch (provider) {
            case "mock" -> mockClientFactory.get();
            case "google" -> googleClientFactory.get();
            default -> throw new IllegalArgumentException("不支持的 AI provider: " + aiProperties.getProvider());
        };
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "google";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
