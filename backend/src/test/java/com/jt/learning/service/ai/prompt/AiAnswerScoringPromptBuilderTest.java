package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiAnswerScoringPromptBuilderTest {

    private final AiAnswerScoringPromptBuilder promptBuilder = new AiAnswerScoringPromptBuilder(new ObjectMapper());

    @Test
    void buildShouldIncludeQuestionAnswersTagsErrorTypesAndUserAnswer() {
        Question question = new Question();
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("明天下午我要去公园散步。");
        question.setContextText("朋友之间的日常对话。");
        question.setLevel("N4");
        question.setDifficulty(3);
        question.setGrammarPoint("助词");
        question.setSpoken(true);
        question.setBusiness(false);
        question.setExam(false);

        QuestionAnswer standardAnswer = new QuestionAnswer();
        standardAnswer.setAnswerText("明日の午後、公園を散歩します。");
        standardAnswer.setAnswerType("STANDARD");
        standardAnswer.setPrimaryAnswer(true);
        standardAnswer.setSortOrder(0);

        AiQuestionPrompt prompt = promptBuilder.build(
                question,
                List.of(standardAnswer),
                List.of(new AiQuestionTagOptionDTO("DAILY_LIFE", "日常生活", "日常场景")),
                List.of(new AiErrorTypeOptionDTO(
                        7L,
                        "PARTICLE",
                        "助词错误",
                        "助词选择或用法不正确",
                        "GRAMMAR_SYNTAX",
                        "语法与句法"
                )),
                new AiAnswerScoringRequest("明日の午後、公園で散歩します。")
        );

        assertThat(prompt.systemPrompt())
                .contains("errorTypeCode")
                .contains("TRANSLATION_ZH_TO_JA")
                .contains("grammarPoint 仅是学习参考")
                .contains("日常口语中自然省略的主语")
                .contains("不得使用“助词错误”");
        assertThat(prompt.userPrompt())
                .contains("DAILY_LIFE")
                .contains("PARTICLE")
                .contains("公園で散歩します")
                .contains("suggestedUserErrorTypeDescription")
                .contains("赶上交通工具时误用を而非に")
                .contains("grammarVocabularyScore");
    }
}
