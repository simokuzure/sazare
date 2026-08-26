package com.jt.learning.service.ai.client;

import com.jt.learning.dto.JapaneseCorrectionRequest;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiJapaneseCorrectionClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void correctShouldUseEnglishExplanationsInEnglishMode() throws Exception {
        MockAiJapaneseCorrectionClient client = new MockAiJapaneseCorrectionClient(objectMapper);

        String result = client.correct(
                new AiQuestionPrompt("system", "user"),
                new JapaneseCorrectionRequest("これは自然な日本語です。", "EN_TO_JA")
        );

        JsonNode review = objectMapper.readTree(result).get("review");
        assertThat(review.get("comments").get("grammarVocabularyComment").asString())
                .isEqualTo("The grammar and vocabulary are generally correct.");
        assertThat(review.get("errorAnalysis").get(0).get("issue").asString())
                .isEqualTo("The phrasing could be more natural.");
        assertThat(review.get("revisionSuggestions").get(0).asString())
                .startsWith("Check whether");
        assertThat(review.get("recommendedExpressions").get(0).get("usage").asString())
                .startsWith("Use when");
        assertThat(review.get("correctedText").asString()).isEqualTo("これは自然な日本語です。");
    }
}
