package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.JapaneseCorrectionRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiJapaneseCorrectionPromptBuilderTest {

    @Test
    void shouldBuildPureJapaneseCorrectionContract() {
        var builder = new AiJapaneseCorrectionPromptBuilder(new ObjectMapper());

        var prompt = builder.build(
                List.of(new AiErrorTypeOptionDTO(
                        1L, "PARTICLE", "助词错误", "说明", "GRAMMAR_SYNTAX", "语法与句法")),
                new JapaneseCorrectionRequest("図書館を行きました。")
        );

        assertThat(prompt.systemPrompt())
                .contains("没有中文原文或参考答案")
                .contains("不得判断漏译、误译、过度发挥或中文直译")
                .contains("不检查标点")
                .contains("不得仅为调整标点而改写 correctedText")
                .contains("informationCompletenessScore 评表记与输入完整性")
                .doesNotContain("informationCompletenessScore 评表记、标点与输入完整性");
        assertThat(prompt.userPrompt())
                .contains("correctedText")
                .contains("reviewSourceText")
                .doesNotContain("sentenceReviews")
                .doesNotContain("referenceText");
    }
}
