package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
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
                new AiQuestionTagOptionDTO("NARRATIVE", "叙事文", "叙事体裁")
        );

        assertThat(prompt.systemPrompt())
                .contains("grammarPoint 字段用于生词提示，不是语法点")
                .contains("中文词语：日语表达（读音）")
                .contains("专用名称出现时应优先提示")
                .contains("不得提供完整句子的日语翻译");
        assertThat(prompt.userPrompt())
                .contains("\"grammarPoint\": \"郊外：郊外（こうがい）")
                .contains("专用名称：对应日语名称（读音）");
    }
}
