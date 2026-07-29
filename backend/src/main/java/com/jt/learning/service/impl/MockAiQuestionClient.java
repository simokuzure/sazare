package com.jt.learning.service.impl;

import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.service.AiQuestionClient;
import com.jt.learning.service.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MockAiQuestionClient implements AiQuestionClient {

    private final ObjectMapper objectMapper;

    public MockAiQuestionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateQuestions(
            AiQuestionPrompt prompt,
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (int i = 0; i < request.questionCount(); i++) {
            questions.add(buildQuestion(request, sceneTagOptions, functionTagOptions, i));
        }

        try {
            return objectMapper.writeValueAsString(Map.of("questions", questions));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock AI JSON 序列化失败", exception);
        }
    }

    private Map<String, Object> buildQuestion(
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions,
            int index
    ) {
        AiQuestionTagOptionDTO sceneTag = sceneTagOptions.get(index % sceneTagOptions.size());
        List<String> tagCodes = new ArrayList<>();
        tagCodes.add(sceneTag.code());
        if (!functionTagOptions.isEmpty()) {
            tagCodes.add(functionTagOptions.get(index % functionTagOptions.size()).code());
        }

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionType", "TRANSLATION_ZH_TO_JA");
        question.put("sourceText", buildSourceText(index));
        question.put("contextText", "日常交流中表达计划或请求。");
        question.put("level", request.level());
        question.put("difficulty", request.difficulty());
        question.put("grammarPoint", "基本句型和自然表达");
        question.put("spoken", true);
        question.put("business", false);
        question.put("exam", false);
        question.put("tagCodes", tagCodes);
        question.put("answers", buildAnswers(index));
        return question;
    }

    private String buildSourceText(int index) {
        List<String> sourceTexts = List.of(
                "我今天下午要去银行办理转账。",
                "如果明天下雨，我们就在家学习吧。",
                "请告诉我车站在哪里。",
                "我想预约明天的会议。",
                "我昨天买了一本日语书。"
        );
        return sourceTexts.get(index % sourceTexts.size());
    }

    private List<Map<String, Object>> buildAnswers(int index) {
        List<String> answers = List.of(
                "今日の午後、銀行へ振り込みに行きます。",
                "明日雨が降ったら、家で勉強しましょう。",
                "駅はどこですか。",
                "明日の会議を予約したいです。",
                "昨日、日本語の本を買いました。"
        );

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("answerText", answers.get(index % answers.size()));
        answer.put("answerType", "STANDARD");
        answer.put("primaryAnswer", true);
        answer.put("sortOrder", 0);
        return List.of(answer);
    }
}
