package com.sazare.service.ai.client;

import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewQuestionClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MockAiReviewQuestionClient implements AiReviewQuestionClient {

    private final ObjectMapper objectMapper;
    private final AtomicInteger sequence = new AtomicInteger();

    public MockAiReviewQuestionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateQuestion(AiQuestionPrompt prompt) {
        return generateQuestion(prompt, List.of(), List.of());
    }

    @Override
    public String generateQuestion(
            AiQuestionPrompt prompt,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        int number = sequence.incrementAndGet();
        Map<String, Object> question = new java.util.LinkedHashMap<>();
        question.put("sourceText", "请用日语表达：我会再次认真确认这个表达（复习" + number + "）。");
        question.put("contextText", "学习者在日常交流中确认自己的表达。");
        question.put("grammarPoint", "根据复习重点选择自然表达。");
        question.put("tagCodes", selectedTagCodes(sceneTagOptions, functionTagOptions));
        question.put("answers", List.of(Map.of(
                "answerText", "この表現をもう一度しっかり確認します。",
                "answerType", "STANDARD",
                "primaryAnswer", true,
                "sortOrder", 0
        )));
        try {
            return objectMapper.writeValueAsString(Map.of("question", question));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 复习生题 JSON 序列化失败", exception);
        }
    }

    @Override
    public String classifyTags(
            AiQuestionPrompt prompt,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "tagCodes", selectedTagCodes(sceneTagOptions, functionTagOptions)));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 复习题标签 JSON 序列化失败", exception);
        }
    }

    private List<String> selectedTagCodes(
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        if (sceneTagOptions.isEmpty()) {
            return List.of();
        }
        if (functionTagOptions.isEmpty()) {
            return List.of(sceneTagOptions.getFirst().code());
        }
        return List.of(sceneTagOptions.getFirst().code(), functionTagOptions.getFirst().code());
    }
}
