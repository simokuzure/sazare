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

    @Test
    void buildArticleShouldRequireCompleteErrorAnalysisItems() {
        Question question = new Question();
        question.setQuestionType("TRANSLATION_ZH_TO_JA_ARTICLE");
        question.setSourceText("上周，我和朋友去了京都。\n\n天气不好，但我们玩得很开心。");
        question.setContextText("AI 原创叙事文。");
        question.setLevel("N3");
        question.setDifficulty(3);
        question.setGrammarPoint("时态和篇章衔接");
        question.setSpoken(false);
        question.setBusiness(false);
        question.setExam(false);

        QuestionAnswer standardAnswer = new QuestionAnswer();
        standardAnswer.setAnswerText("先週、友人と京都へ行きました。\n\n天気は悪かったですが、楽しく過ごしました。");

        AiQuestionPrompt prompt = promptBuilder.build(
                question,
                List.of(standardAnswer),
                List.of(new AiQuestionTagOptionDTO("NARRATIVE", "叙事文", "叙事体裁")),
                List.of(new AiErrorTypeOptionDTO(
                        8L,
                        "UNNATURAL_EXPRESSION",
                        "不自然表达",
                        "表达不符合日语习惯",
                        "VOCABULARY_EXPRESSION",
                        "词汇与表达"
                )),
                new AiAnswerScoringRequest("先週、友達と京都に行きました。天気が悪いですが、楽しかったです。")
        );

        assertThat(prompt.systemPrompt())
                .contains("issue")
                .contains("每个错误项")
                .contains("不要返回空对象或空字段")
                .contains("不得改写文字、标点或空白")
                .contains("无法精确截取时使用用户完整答案");
        assertThat(prompt.userPrompt())
                .contains("\"issue\": \"中文问题说明\"")
                .contains("suggestedUserErrorTypeName")
                .contains("没有明确错误时，errorAnalysis 必须返回 []")
                .contains("不得返回 issue 等字段为空的对象")
                .contains("\"expression\": \"日语推荐表达\"")
                .contains("没有推荐表达时，recommendedExpressions 必须返回 []");
    }

    @Test
    void buildShouldRequestEnglishFeedbackForEnglishQuestion() {
        Question question = new Question();
        question.setQuestionType("TRANSLATION_EN_TO_JA");
        question.setSourceText("Please tell me where the station is.");
        question.setContextText("Everyday conversation.");
        question.setLevel("N4");
        question.setDifficulty(2);
        question.setGrammarPoint("Indirect question");
        question.setSpoken(true);
        question.setBusiness(false);
        question.setExam(false);

        QuestionAnswer answer = new QuestionAnswer();
        answer.setAnswerText("駅がどこにあるか教えてください。");
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);

        AiQuestionPrompt prompt = promptBuilder.build(
                question, List.of(answer), List.of(), List.of(),
                new AiAnswerScoringRequest("駅はどこか教えてください。"));

        assertThat(prompt.systemPrompt())
                .contains("all explanatory output must be English")
                .contains("comments field")
                .contains("revisionSuggestions[]")
                .contains("recommendedExpressions[].usage")
                .contains("must remain Japanese")
                .contains("TRANSLATION_EN_TO_JA");
        assertThat(prompt.userPrompt())
                .contains("\"grammarComment\": \"English grammar explanation\"")
                .contains("\"revisionSuggestions\": [\"English revision suggestion\"]")
                .contains("\"usage\": \"English usage context\"")
                .doesNotContain("使用简洁中文")
                .doesNotContain("\"grammarComment\": \"中文语法说明\"")
                .doesNotContain("\"revisionSuggestions\": [\"中文修改建议\"]");
    }
}
