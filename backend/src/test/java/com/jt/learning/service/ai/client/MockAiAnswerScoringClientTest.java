package com.jt.learning.service.ai.client;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiAnswerScoringClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void scoreAnswerShouldReturnCompleteReviewJson() throws Exception {
        MockAiAnswerScoringClient client = new MockAiAnswerScoringClient(objectMapper);
        QuestionAnswer answer = new QuestionAnswer();
        answer.setAnswerText("今日の午後、銀行へ振り込みに行きます。");
        Question question = new Question();
        question.setQuestionType("TRANSLATION_ZH_TO_JA");

        String result = client.scoreAnswer(
                new AiQuestionPrompt("system", "user"),
                new AiAnswerScoringRequest("今日の午後、銀行に送金をしに行きます。"),
                question,
                List.of(answer),
                List.of(new AiQuestionTagOptionDTO("FINANCE_BANK", "银行", "金融场景标签"))
        );

        JsonNode review = objectMapper.readTree(result).get("review");
        assertThat(review.get("scores").get("grammarVocabularyScore").asInt()).isEqualTo(82);
        assertThat(review.get("overallComment").asString()).contains("整体意思");
        assertThat(review.get("errorAnalysis")).hasSize(1);
        assertThat(review.get("recommendedExpressions").get(0).get("expression").asString())
                .isEqualTo("今日の午後、銀行へ振り込みに行きます。");
    }

    @Test
    void scoreAnswerShouldUseEnglishExplanationsForEnglishQuestion() throws Exception {
        MockAiAnswerScoringClient client = new MockAiAnswerScoringClient(objectMapper);
        Question question = new Question();
        question.setQuestionType("TRANSLATION_EN_TO_JA");
        QuestionAnswer answer = new QuestionAnswer();
        answer.setAnswerText("健康のために、毎日少なくとも30分は歩くようにしています。");

        String result = client.scoreAnswer(
                new AiQuestionPrompt("system", "user"),
                new AiAnswerScoringRequest("健康のために、毎日30分歩くようにしています。"),
                question,
                List.of(answer),
                List.of()
        );

        JsonNode review = objectMapper.readTree(result).get("review");
        assertThat(review.get("comments").get("grammarComment").asString())
                .isEqualTo("The grammar is generally correct.");
        assertThat(review.get("revisionSuggestions").get(0).asString())
                .startsWith("Check whether");
        assertThat(review.get("recommendedExpressions").get(0).get("usage").asString())
                .isEqualTo("Suitable for contexts similar to this question.");
        assertThat(review.get("recommendedExpressions").get(0).get("expression").asString())
                .isEqualTo("健康のために、毎日少なくとも30分は歩くようにしています。");
    }
}
