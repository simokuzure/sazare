package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.service.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAnswerScoringPromptBuilderTest {

    private final AiAnswerScoringPromptBuilder promptBuilder = new AiAnswerScoringPromptBuilder(new ObjectMapper());

    @Test
    void buildShouldIncludeQuestionAnswersTagsAndUserAnswer() {
        Question question = new Question();
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("我今天下午要去银行办理转账。");
        question.setContextText("日常生活中说明下午的计划。");
        question.setLevel("N4");
        question.setDifficulty(3);
        question.setGrammarPoint("予定を表す表現");
        question.setSpoken(true);
        question.setBusiness(false);
        question.setExam(false);

        QuestionAnswer standardAnswer = new QuestionAnswer();
        standardAnswer.setAnswerText("今日の午後、銀行へ振り込みに行きます。");
        standardAnswer.setAnswerType("STANDARD");
        standardAnswer.setPrimaryAnswer(true);
        standardAnswer.setSortOrder(0);

        List<AiQuestionTagOptionDTO> tagOptions = List.of(
                new AiQuestionTagOptionDTO("FINANCE_BANK", "银行", "金融场景标签")
        );

        AiQuestionPrompt prompt = promptBuilder.build(
                question,
                List.of(standardAnswer),
                tagOptions,
                new AiAnswerScoringRequest("今日の午後、銀行に送金をしに行きます。")
        );

        assertThat(prompt.systemPrompt())
                .contains("日语学习评分助手")
                .contains("TRANSLATION_ZH_TO_JA");
        assertThat(prompt.userPrompt())
                .contains("中文原文：我今天下午要去银行办理转账。")
                .contains("FINANCE_BANK")
                .contains("今日の午後、銀行へ振り込みに行きます。")
                .contains("今日の午後、銀行に送金をしに行きます。")
                .contains("grammarVocabularyScore")
                .contains("recommendedExpressions");
    }
}
