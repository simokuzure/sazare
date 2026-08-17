package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.UserErrorType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewPromptBuilderTest {

    @Test
    void scoringPromptShouldContainReviewFocusAndQualityContract() {
        var prompt = new AiReviewScoringPromptBuilder(new ObjectMapper()).build(
                userErrorType(), errorType(), question(), List.of(answer()),
                List.of(new AiErrorTypeOptionDTO(1L, "PARTICLE_CASE", "格助词", "说明", "PARTICLE", "助词")),
                "電車に間に合いました"
        );

        assertThat(prompt.systemPrompt()).contains("复习重点", "自然", "quality", "targetErrorResolved", "0到2", "0到100");
        assertThat(prompt.userPrompt()).contains(
                "赶上交通工具时误用を", "電車に間に合いました", "grammarVocabularyScore");
    }

    @Test
    void questionPromptShouldContainExistingQuestionsAndSingleQuestionContract() {
        var prompt = new AiReviewQuestionPromptBuilder(new ObjectMapper()).build(
                userErrorType(), errorType(), List.of(question()), Map.of(10L, List.of(answer())),
                List.of(new AiQuestionTagOptionDTO("DAILY_TRAVEL", "日常出行", "日常交通出行场景")),
                List.of(new AiQuestionTagOptionDTO("FUNCTION_CONFIRM", "确认", "确认信息")));

        assertThat(prompt.systemPrompt())
                .contains("复习重点", "一道", "1到10个答案")
                .contains("contextText只能客观说明", "不得包含考查意图", "语法或词汇要求只写入grammarPoint");
        assertThat(prompt.userPrompt()).contains(
                "请翻译", "電車に間に合いました", "DAILY_TRAVEL", "FUNCTION_CONFIRM", "tagCodes");
    }

    @Test
    void tagPromptShouldRequireReclassificationFromSentenceMeaning() {
        var prompt = new AiReviewTagPromptBuilder(new ObjectMapper()).build(
                question(),
                List.of(new AiQuestionTagOptionDTO("DAILY_TRAVEL", "日常出行", "日常交通出行场景")),
                List.of(new AiQuestionTagOptionDTO("FUNCTION_CONFIRM", "确认", "确认信息")));

        assertThat(prompt.systemPrompt()).contains("实际语义重新选择", "不得照搬文章体裁标签", "1个场景标签");
        assertThat(prompt.userPrompt()).contains("请翻译", "DAILY_TRAVEL", "FUNCTION_CONFIRM", "tagCodes");
    }

    private UserErrorType userErrorType() {
        UserErrorType type = new UserErrorType();
        type.setName("赶上交通工具时误用を");
        type.setDescription("表示赶上交通工具时应使用に。");
        return type;
    }

    private ErrorType errorType() {
        ErrorType type = new ErrorType();
        type.setCode("PARTICLE_CASE");
        type.setName("格助词");
        return type;
    }

    private Question question() {
        Question question = new Question();
        question.setId(10L);
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("请翻译");
        question.setContextText("日常交流");
        question.setLevel("N4");
        question.setDifficulty(2);
        question.setGrammarPoint("に間に合う");
        question.setSpoken(true);
        question.setBusiness(false);
        question.setExam(false);
        return question;
    }

    private QuestionAnswer answer() {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(10L);
        answer.setAnswerText("電車に間に合いました");
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        return answer;
    }
}
