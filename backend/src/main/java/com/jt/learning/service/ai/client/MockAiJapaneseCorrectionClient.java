package com.jt.learning.service.ai.client;

import com.jt.learning.dto.JapaneseCorrectionRequest;
import com.jt.learning.service.ai.AiJapaneseCorrectionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockAiJapaneseCorrectionClient implements AiJapaneseCorrectionClient {

    private final ObjectMapper objectMapper;

    public MockAiJapaneseCorrectionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String correct(AiQuestionPrompt prompt, JapaneseCorrectionRequest request) {
        String original = request.japaneseText().trim();
        String corrected = "これは自然な日本語です。";
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("scores", Map.of(
                "grammarVocabularyScore", 82,
                "naturalFluencyScore", 80,
                "scenarioAdaptationScore", 84,
                "informationCompletenessScore", 88
        ));
        review.put("totalScore", 83.50);
        review.put("correctedText", corrected);
        review.put("overallComment", "基本意思清楚，修订后表达更自然。" );
        review.put("comments", Map.of(
                "grammarVocabularyComment", "语法与词汇基本正确。",
                "naturalFluencyComment", "个别表达可以更符合日语习惯。",
                "styleConsistencyComment", "语体保持一致。",
                "writingCompletenessComment", "表记和输入内容完整。"
        ));
        review.put("errorAnalysis", List.of(Map.of(
                "errorTypeCode", "UNNATURAL_EXPRESSION",
                "original", original,
                "issue", "表达可以更自然。",
                "suggestion", corrected,
                "reviewSourceText", "这是自然的日语。",
                "severity", "MEDIUM",
                "suggestedUserErrorTypeName", "使用不自然的整体表达",
                "suggestedUserErrorTypeDescription", "句意可理解，但整体表达不符合常见日语习惯时，改用自然的固定表达。"
        )));
        review.put("revisionSuggestions", List.of("检查搭配和句尾语体是否自然。"));
        review.put("recommendedExpressions", List.of(Map.of(
                "expression", corrected,
                "usage", "表达某段日语自然无误时。",
                "formality", "NEUTRAL",
                "note", "可根据上下文调整礼貌程度。"
        )));

        try {
            return objectMapper.writeValueAsString(Map.of("review", review));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 日语纠错 JSON 序列化失败", exception);
        }
    }
}
