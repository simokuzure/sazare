package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiArticleRetryContext;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.dto.QuestionEmbeddingMatch;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AiArticleQuestionPromptBuilderTest {

    private final AiArticleQuestionPromptBuilder promptBuilder =
            new AiArticleQuestionPromptBuilder(new ObjectMapper());

    @Test
    void buildShouldUseGrammarPointAsVocabularyHints() {
        AiQuestionPrompt prompt = promptBuilder.build(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", "旅行", null),
                new AiQuestionTagOptionDTO("NARRATIVE", "叙事文", "叙事体裁"),
                "123e4567-e89b-12d3-a456-426614174000",
                null
        );

        assertThat(prompt.systemPrompt())
                .contains("grammarPoint 字段用于生词提示，不是语法点")
                .contains("中文词语：日语表达（读音）")
                .contains("专用名称出现时应优先提示")
                .contains("不得提供完整句子的日语翻译")
                .contains("周末与朋友出游—发现小店或风景—愉快结束")
                .contains("人物不能只是传递信息或推动剧情的工具")
                .contains("不得提前写死最终成功、失败、和解、分手、感悟或解决方案")
                .contains("不要为了大胆而堆砌猎奇元素")
                .contains("不得连续套用“姓名＋动作＋说/问/回答/反驳＋台词”的整齐句式")
                .contains("不要为了显得生动而给每句话装饰")
                .contains("人物可以说得含糊、说错、改口或只说半句")
                .contains("允许开放状态、未解决冲突和非圆满结尾");
        assertThat(prompt.userPrompt())
                .contains("123e4567-e89b-12d3-a456-426614174000")
                .contains("\"subject\" : \"叙述对象\"")
                .contains("\"changeOrInsight\" : \"变化或感悟\"")
                .contains("蓝图应保留发展空间，不得概括完整剧情或预先给出结局")
                .contains("\"grammarPoint\": \"郊外：郊外（こうがい）")
                .contains("专用名称：对应日语名称（读音）");
    }

    @Test
    void buildShouldIncludeDuplicateRejectionContext() {
        AiQuestionPrompt prompt = promptBuilder.build(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null),
                new AiQuestionTagOptionDTO("NARRATIVE", "叙事文", "叙事体裁"),
                "123e4567-e89b-12d3-a456-426614174001",
                new AiArticleRetryContext(
                        "正文向量与 1 篇历史文章相似，最高相似度为 0.9200，达到拒绝阈值 0.80。",
                        "本次被拒绝的文章正文。",
                        java.util.List.of(new QuestionEmbeddingMatch(47L, "历史文章正文。", 0.92d))
                )
        );

        assertThat(prompt.userPrompt())
                .contains("上一篇文章因正文语义重复被拒绝")
                .contains("本次被拒绝的文章正文。")
                .contains("历史文章正文。")
                .contains("不能只替换地名、人物、物品或近义词");
    }
}
