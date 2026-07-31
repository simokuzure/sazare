package com.jt.learning.config;

import com.jt.learning.service.AiQuestionClient;
import com.jt.learning.service.AiAnswerScoringClient;
import com.jt.learning.service.impl.GoogleAiAnswerScoringClient;
import com.jt.learning.service.impl.GoogleAiQuestionClient;
import com.jt.learning.service.impl.JavaAiProviderHttpClient;
import com.jt.learning.service.impl.MockAiAnswerScoringClient;
import com.jt.learning.service.impl.MockAiQuestionClient;
import com.jt.learning.service.impl.AiProviderHttpClient;
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
    public AiProviderHttpClient aiProviderHttpClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new JavaAiProviderHttpClient(httpClient);
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
