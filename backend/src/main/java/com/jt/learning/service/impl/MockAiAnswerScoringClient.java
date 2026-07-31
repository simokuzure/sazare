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
        review.put("overallComment", "整体意思基本准确，语法和用词可以再自然一些。");
        review.put("comments", Map.of(
                "grammarComment", "句子结构基本正确，助词使用需要继续注意。",
                "vocabularyComment", "核心词汇能表达原意，但部分搭配不够自然。",
                "naturalnessComment", "表达可以理解，不过和日语母语者常用说法仍有距离。",
                "scenarioComment", "语气基本符合题目场景，敬体表达还可以更稳定。"
        ));
        review.put("errorAnalysis", List.of(Map.of(
                "type", "NATURALNESS",
                "original", request.answerText().trim(),
                "issue", "表达能传达大意，但整体不够像自然日语。",
                "suggestion", "参考标准答案调整助词和动词搭配。",
                "severity", "MEDIUM"
        )));
        review.put("revisionSuggestions", List.of(
                "先确认中文原文中的时间、动作和对象是否完整保留。",
                "优先使用标准答案中的自然搭配，再替换成自己的表达。"
        ));
        review.put("recommendedExpressions", List.of(Map.of(
                "expression", standardAnswers.getFirst().getAnswerText(),
                "usage", "适合本题语境的基础推荐表达。",
                "formality", "POLITE",
                "note", "可以作为当前题目的优先记忆表达。"
        )));

        try {
            return objectMapper.writeValueAsString(Map.of("review", review));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock AI JSON 序列化失败", exception);
        }
    }
}
