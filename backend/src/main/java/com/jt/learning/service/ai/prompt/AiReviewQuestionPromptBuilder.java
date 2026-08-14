package com.jt.learning.service.ai.prompt;

import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.UserErrorType;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiReviewQuestionPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是日语翻译复习题生成助手。只输出一个合法 JSON 对象，不要输出 Markdown 或额外说明。
            生成一道与目标错误模式直接相关、但题干不重复的中译日题目。
            sourceText必须是非空中文题干；contextText和grammarPoint必须非空。
            contextText只能客观说明对话场景、人物关系或事情背景，不得包含考查意图、作答方向、候选词语、语法点、参考答案或纠错提示。
            不得在contextText中使用“用于考查”“应使用”“而非”等会暴露答案方向的表述；语法或词汇要求只写入grammarPoint。
            answers返回1到10个答案；answerType只能是STANDARD或REFERENCE；必须且只能有一个primaryAnswer=true；
            主答案必须是STANDARD。答案文本不得重复，sortOrder必须是从0开始的不重复非负整数。
            不输出等级、难度、标签或场景标记，这些元数据由后端继承。
            """;

    private final ObjectMapper objectMapper;

    public AiReviewQuestionPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            UserErrorType userErrorType,
            ErrorType errorType,
            List<Question> cycleQuestions,
            Map<Long, List<QuestionAnswer>> answersByQuestionId
    ) {
        List<Map<String, Object>> existingQuestions = cycleQuestions.stream()
                .map(question -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("sourceText", question.getSourceText());
                    item.put("contextText", question.getContextText());
                    item.put("grammarPoint", question.getGrammarPoint());
                    item.put("answers", answersByQuestionId.getOrDefault(question.getId(), List.of()));
                    return item;
                })
                .toList();

        Map<String, Object> targetError = new LinkedHashMap<>();
        targetError.put("userErrorTypeName", userErrorType.getName());
        targetError.put("userErrorTypeDescription", userErrorType.getDescription());
        targetError.put("errorTypeCode", errorType.getCode());
        targetError.put("errorTypeName", errorType.getName());

        String userPrompt = """
                请根据目标错误类型和本周期已有题目生成一道新的复习衍生题。

                目标错误类型：%s
                本周期已有题目及答案：%s

                JSON结构：
                {"question":{"sourceText":"中文题干","contextText":"中文语境","grammarPoint":"语法点","answers":[{"answerText":"日语答案","answerType":"STANDARD","primaryAnswer":true,"sortOrder":0}]}}
                """.formatted(toJson(targetError), toJson(existingQuestions));
        return new AiQuestionPrompt(SYSTEM_PROMPT, userPrompt);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("复习生题 Prompt JSON 序列化失败", exception);
        }
    }
}
