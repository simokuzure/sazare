package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionPromptBuilderTest {

    private final AiQuestionPromptBuilder promptBuilder = new AiQuestionPromptBuilder(new ObjectMapper());

    @Test
    void buildShouldIncludeTagOptionsFromDatabaseCandidates() {
        AiQuestionGenerationRequest request = new AiQuestionGenerationRequest(
                2,
                "N4",
                3,
                List.of("DAILY_LIFE_WEATHER"),
                List.of("FUNCTION_PROPOSE_PLAN"),
                List.of("如果明天下雨，我们就在家学习吧。"),
                "偏口语"
        );
        List<AiQuestionTagOptionDTO> sceneTagOptions = List.of(
                new AiQuestionTagOptionDTO("DAILY_LIFE_WEATHER", "天气", "日常生活场景标签")
        );
        List<AiQuestionTagOptionDTO> functionTagOptions = List.of(
                new AiQuestionTagOptionDTO("FUNCTION_PROPOSE_PLAN", "提出计划", "意愿功能标签")
        );

        AiQuestionPrompt prompt = promptBuilder.build(request, sceneTagOptions, functionTagOptions);

        assertThat(prompt.systemPrompt()).contains("TRANSLATION_ZH_TO_JA");
        assertThat(prompt.userPrompt())
                .contains("题目数量：2")
                .contains("JLPT 等级：N4")
                .contains("难度：3")
                .contains("DAILY_LIFE_WEATHER")
                .contains("FUNCTION_PROPOSE_PLAN")
                .contains("如果明天下雨，我们就在家学习吧。")
                .contains("偏口语");
    }

    @Test
    void buildShouldUseEnglishDirectionRules() {
        AiQuestionGenerationRequest request = new AiQuestionGenerationRequest(
                1, "N3", 3, List.of(), List.of(), List.of(), null, "EN_TO_JA");

        AiQuestionPrompt prompt = promptBuilder.build(request, List.of(), List.of());

        assertThat(prompt.systemPrompt())
                .contains("TRANSLATION_EN_TO_JA")
                .contains("all explanatory output must be English");
        assertThat(prompt.userPrompt()).contains("English-to-Japanese");
    }
}
