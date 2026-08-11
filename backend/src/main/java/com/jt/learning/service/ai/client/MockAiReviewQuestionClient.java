package com.jt.learning.service.ai.client;

import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.AiReviewQuestionClient;
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
        int number = sequence.incrementAndGet();
        Map<String, Object> question = Map.of(
                "sourceText", "请用日语表达：我会再次认真确认这个表达（复习" + number + "）。",
                "contextText", "学习者在日常交流中确认自己的表达。",
                "grammarPoint", "根据目标错误类型选择自然表达。",
                "answers", List.of(Map.of(
                        "answerText", "この表現をもう一度しっかり確認します。",
                        "answerType", "STANDARD",
                        "primaryAnswer", true,
                        "sortOrder", 0
                ))
        );
        try {
            return objectMapper.writeValueAsString(Map.of("question", question));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 复习生题 JSON 序列化失败", exception);
        }
    }
}
