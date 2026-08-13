package com.jt.learning.service.ai.client;

import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.service.ai.AiQuestionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
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

    @Override
    public String generateArticle(AiQuestionPrompt prompt, AiArticleGenerationRequest request) {
        List<String> chineseSentences = List.of(
                "上周末，我和朋友决定去郊外的一座小镇旅行。",
                "我们原本计划乘早班电车出发，却因为看错时间错过了车。",
                "下一班车要等一个小时，所以我们在车站附近吃了早餐。",
                "到达小镇时，天空突然下起了大雨。",
                "我们没有带伞，只好跑进一家旧书店避雨。",
                "店主热情地介绍了当地的历史，还推荐了一家安静的咖啡馆。",
                "雨停以后，我们按照他的建议慢慢参观了老街。",
                "虽然行程和预想完全不同，但这次意外让旅行变得更加难忘。"
        );
        List<String> japaneseSentences = List.of(
                "先週末、友人と郊外の小さな町へ旅行することにしました。",
                "朝早い電車で出発する予定でしたが、時間を見間違えて乗り遅れてしまいました。",
                "次の電車まで一時間あったので、駅の近くで朝食を取りました。",
                "町に着くと、空から急に激しい雨が降り始めました。",
                "傘を持っていなかったため、古い本屋に駆け込んで雨宿りをしました。",
                "店主は親切に町の歴史を教え、静かな喫茶店も紹介してくれました。",
                "雨がやんだ後、私たちは彼の勧めに従って古い町並みをゆっくり見て回りました。",
                "予定とはまったく違う旅になりましたが、その偶然のおかげで忘れられない思い出になりました。"
        );
        List<Map<String, Object>> sentences = new ArrayList<>();
        for (int index = 0; index < chineseSentences.size(); index++) {
            sentences.add(Map.of(
                    "index", index,
                    "chineseText", chineseSentences.get(index),
                    "japaneseReference", japaneseSentences.get(index)
            ));
        }
        Map<String, Object> article = new LinkedHashMap<>();
        article.put("questionType", "TRANSLATION_ZH_TO_JA_ARTICLE");
        article.put("contextText", "叙事文，使用自然且连贯的书面语。");
        article.put("level", request.level());
        article.put("difficulty", request.difficulty());
        article.put("grammarPoint", "郊外：郊外（こうがい）\n早班电车：始発電車（しはつでんしゃ）\n避雨：雨宿り（あまやどり）\n老街：古い町並み（ふるいまちなみ）");
        article.put("spoken", false);
        article.put("business", false);
        article.put("exam", false);
        article.put("sentences", sentences);
        try {
            return objectMapper.writeValueAsString(Map.of("article", article));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Mock 文章 AI JSON 序列化失败", exception);
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
