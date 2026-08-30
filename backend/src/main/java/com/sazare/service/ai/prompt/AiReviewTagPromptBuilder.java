package com.sazare.service.ai.prompt;

import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.entity.Question;
import com.sazare.service.ai.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class AiReviewTagPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是日语复习题标签分类助手。只输出一个合法 JSON 对象，不要输出 Markdown 或额外说明。
            根据复习句子的实际语义重新选择标签，不得继承来源题标签，也不得使用文章体裁标签。
            候选列表只包含二级标签。tagCodes必须包含且只能包含1个二级场景标签，可以再包含0到2个二级功能标签，不能使用候选列表之外的code。
            """;

    private final ObjectMapper objectMapper;

    public AiReviewTagPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            Question question,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        String userPrompt = """
                请为下面的复习句重新判断场景与功能标签。

                复习句：
                {"sourceText":%s,"contextText":%s,"grammarPoint":%s}

                sceneTagOptions：%s
                functionTagOptions：%s

                JSON结构：
                {"tagCodes":["场景标签code","可选的功能标签code"]}
                """.formatted(
                toJson(question.getSourceText()),
                toJson(question.getContextText()),
                toJson(question.getGrammarPoint()),
                toJson(sceneTagOptions),
                toJson(functionTagOptions));
        return new AiQuestionPrompt(SYSTEM_PROMPT, userPrompt);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("复习题标签 Prompt JSON 序列化失败", exception);
        }
    }
}
