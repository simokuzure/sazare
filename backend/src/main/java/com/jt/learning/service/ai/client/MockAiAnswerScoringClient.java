package com.jt.learning.service.ai.client;

import com.jt.learning.common.TranslationDirection;
import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.service.ai.AiAnswerScoringClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockAiAnswerScoringClient implements AiAnswerScoringClient {

    private final ObjectMapper objectMapper;

    public MockAiAnswerScoringClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String scoreAnswer(
            AiQuestionPrompt prompt,
            AiAnswerScoringRequest request,
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions
    ) {
        TranslationDirection direction = TranslationDirection.fromQuestionType(question.getQuestionType());
        boolean english = direction == TranslationDirection.EN_TO_JA;
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("scores", Map.of(
                "grammarVocabularyScore", 82,
                "naturalFluencyScore", 78,
                "scenarioAdaptationScore", 80,
                "informationCompletenessScore", 86
        ));
        review.put("totalScore", 81.50);
        review.put("overallComment", english
                ? "The overall meaning is clear, but some phrasing could sound more natural in Japanese."
                : "整体意思基本传达，但部分表达可以更贴近日语习惯。");
        review.put("comments", Map.of(
                "grammarComment", english ? "The grammar is generally correct." : "语法结构基本正确。",
                "vocabularyComment", english ? "The vocabulary conveys the core meaning." : "词汇选择能够表达核心意思。",
                "naturalnessComment", english ? "Some phrasing is slightly literal and could be more natural." : "部分表达略显直译，可以更自然。",
                "scenarioComment", english ? "The register generally fits the context." : "语体与题目场景基本匹配。"
        ));
        if (direction.isArticle(question.getQuestionType())) {
            List<String> sourceSegments = splitSegments(question.getSourceText());
            List<String> referenceSegments = splitSegments(standardAnswers.getFirst().getAnswerText());
            List<Map<String, Object>> sentenceReviews = new java.util.ArrayList<>();
            for (int index = 0; index < sourceSegments.size(); index++) {
                Map<String, Object> sentenceReview = new LinkedHashMap<>();
                sentenceReview.put("sourceSegmentIndex", index);
                sentenceReview.put("sourceText", sourceSegments.get(index));
                sentenceReview.put("referenceText", referenceSegments.get(index));
                sentenceReview.put("answerExcerpt", request.answerText().trim());
                sentenceReview.put("revisedText", referenceSegments.get(index));
                sentenceReview.put("comment", english
                        ? "This sentence was checked against the source meaning and reference translation."
                        : "已按原文语义检查本句，并给出参考修订。" );
                sentenceReviews.add(sentenceReview);
            }
            review.put("sentenceReviews", sentenceReviews);
            review.put("errorAnalysis", List.of());
        } else {
            review.put("errorAnalysis", List.of(Map.of(
                    "errorTypeCode", "UNNATURAL_EXPRESSION",
                    "original", request.answerText().trim(),
                    "issue", english ? "The phrasing is not idiomatic in Japanese." : "表达不符合日语常见说法。",
                    "suggestion", english ? standardAnswers.getFirst().getAnswerText() : "根据语境改用更自然的日语表达。",
                    "severity", "MEDIUM",
                    "suggestedUserErrorTypeName", english ? "Unidiomatic Japanese phrasing" : "不自然表达",
                    "suggestedUserErrorTypeDescription", english
                            ? "Use idiomatic Japanese phrasing instead of translating the source word for word."
                            : "使用符合日语习惯的表达，避免逐字翻译造成不自然的说法。"
            )));
        }
        review.put("revisionSuggestions", english
                ? List.of(
                        "Check whether the sentence follows English word order too closely.",
                        "Choose more natural Japanese phrasing for the context."
                )
                : List.of(
                        "检查句子是否受中文语序影响。",
                        "结合场景选择更自然的日语表达。"
                ));
        review.put("recommendedExpressions", List.of(Map.of(
                "expression", standardAnswers.getFirst().getAnswerText(),
                "usage", english ? "Suitable for contexts similar to this question." : "可用于本题相近语境。",
                "formality", "POLITE",
                "note", english ? "Adjust the level of politeness for the listener." : "根据说话对象调整礼貌程度。"
        )));

        try {
            return objectMapper.writeValueAsString(Map.of("review", review));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock AI JSON 序列化失败", exception);
        }
    }

    private List<String> splitSegments(String text) {
        return List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\\n\\s*\\n"))
                .stream()
                .map(String::trim)
                .toList();
    }
}
