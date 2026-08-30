package com.sazare.service.ai.client;

import com.sazare.common.ArticleLengthTier;
import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiQuestionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockAiQuestionClient client = new MockAiQuestionClient(objectMapper);

    @Test
    void generatedArticlesShouldMatchEveryLengthTierInBothDirections() throws Exception {
        for (TranslationDirection direction : TranslationDirection.values()) {
            for (ArticleLengthTier tier : ArticleLengthTier.values()) {
                AiArticleGenerationRequest request = new AiArticleGenerationRequest(
                        "N3", 3, "NARRATIVE", null, null,
                        direction.learningMode(), tier.name()
                );

                String result = client.generateArticle(
                        new AiQuestionPrompt("system", "user"), request, "mock-seed"
                );
                JsonNode article = objectMapper.readTree(result).get("article");
                StringBuilder sourceText = new StringBuilder();
                for (JsonNode sentence : article.get("sentences")) {
                    sourceText.append(sentence.get("chineseText").asText()).append(' ');
                }
                int length = direction.countArticleLength(sourceText.toString());

                assertThat(length)
                        .as("%s %s", direction, tier)
                        .isBetween(tier.minimum(direction), tier.maximum(direction));
                assertThat(article.get("level").asText()).isEqualTo("N3");
                assertThat(article.get("difficulty").asInt()).isEqualTo(3);
            }
        }
    }
}
