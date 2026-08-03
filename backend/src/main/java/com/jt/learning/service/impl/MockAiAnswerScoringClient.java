package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.service.AiAnswerScoringClient;
import com.jt.learning.service.AiQuestionPrompt;
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
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("scores", Map.of(
                "grammarVocabularyScore", 82,
                "naturalFluencyScore", 78,
                "scenarioAdaptationScore", 80,
                "informationCompletenessScore", 86
        ));
        review.put("totalScore", 81.50);
        review.put("overallComment", "整体意思基本传达，但部分表达可以更贴近日语习惯。");
        review.put("comments", Map.of(
                "grammarComment", "语法结构基本正确。",
                "vocabularyComment", "词汇选择能够表达核心意思。",
                "naturalnessComment", "部分表达略显直译，可以更自然。",
                "scenarioComment", "语体与题目场景基本匹配。"
        ));
        review.put("errorAnalysis", List.of(Map.of(
                "errorTypeCode", "UNNATURAL_EXPRESSION",
                "original", request.answerText().trim(),
                "issue", "表达不符合日语常见说法。",
                "suggestion", "根据语境改用更自然的日语表达。",
                "severity", "MEDIUM",
                "suggestedUserErrorTypeName", "不自然表达",
                "suggestedUserErrorTypeDescription", "使用符合日语习惯的表达，避免逐字翻译造成不自然的说法。"
        )));
        review.put("revisionSuggestions", List.of(
                "检查句子是否受中文语序影响。",
                "结合场景选择更自然的日语表达。"
        ));
        review.put("recommendedExpressions", List.of(Map.of(
                "expression", standardAnswers.getFirst().getAnswerText(),
                "usage", "可用于本题相近语境。",
                "formality", "POLITE",
                "note", "根据说话对象调整礼貌程度。"
        )));

        try {
            return objectMapper.writeValueAsString(Map.of("review", review));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock AI JSON 序列化失败", exception);
        }
    }
}
