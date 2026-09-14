package com.sazare.service.ai.prompt;

import com.sazare.common.TranslationDirection;
import com.sazare.entity.ErrorType;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.UserErrorType;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.service.ai.AiQuestionPrompt;
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
            生成一道与复习重点直接相关、但题干不重复的中译日题目。
            复习重点应自然融入真实的交流目的，不要为展示语法而拼接句子或增加无关难点；在保留复习目标的前提下变化情境和表达，不只替换已有题目的表面内容。
            sourceText必须是非空中文题干；contextText和grammarPoint必须非空。
            先确定人物关系、触发事件和交流目的，独立写出符合源语言母语者习惯的sourceText，再生成日语答案；不得从预设日语句型倒译题干。
            题干应简洁自然，不重复解释双方已知的信息；题干与语境合起来必须足以明确语义，不靠堆砌书面词或语气词制造难度或口语感。
            contextText只能客观说明对话场景、人物关系或事情背景，不得包含考查意图、作答方向、候选词语、语法点、参考答案或纠错提示。
            不得在contextText中使用“用于考查”“应使用”“而非”等会暴露答案方向的表述；语法或词汇要求只写入grammarPoint。
            contextText只补充必要背景，不复述答案，不改变题干含义，也不能事后编造背景迁就不合适的答案。
            grammarPoint在标准答案定稿后填写，只总结其中实际使用且与复习重点相关的语法或表达，不要求组合多个语法点。
            answers返回1到10个答案；answerType只能是STANDARD或REFERENCE；必须且只能有一个primaryAnswer=true；
            主答案必须是STANDARD。答案文本不得重复，sortOrder必须是从0开始的不重复非负整数。
            答案应忠实传达语义、人物关系和语气，但不得按源语言的表面结构逐词翻译。日常口语中能从上下文明确推断的主语、话题和已知宾语应自然省略，不得把“我/你”或“I/you”机械翻成「わたし」「あなた」，尤其不得默认用「あなた」直接称呼对方。
            每个答案都必须保留原文的信息来源、确定程度、动作归属、具体事实和逻辑关系，不得遗漏、模糊或无依据地增添含义。
            所有答案的礼貌程度、情感态度和语气强弱必须符合原文及既定语境。语体由人物关系、场合和交流目的决定；口语和书面表达各自符合实际用途，不将自然表达等同于一律口语化。
            STANDARD优先选择自然、准确且能体现复习重点的表达。REFERENCE可以改变措辞或句式，但必须遵守相同的语义和语体要求，不为凑变体而改变信息或语气；没有合适变体时只返回标准答案，不强迫合理替代表达使用同一句型。
            只有省略会造成歧义，或确有对比、强调、确认人物身份等语用需要时，才显式使用人称表达；需要称呼时应根据人物关系选择自然的姓名、职务或关系称呼。
            不输出等级、难度或场景标记。tagCodes必须根据新题实际语义重新选择，不能照搬已有题标签。
            候选列表只包含二级标签。tagCodes必须包含且只能包含1个二级场景标签，可以再包含0到2个二级功能标签，不能使用候选列表之外的code。
            输出前检查题干独立自然、复习重点得到体现、所有答案语义完整且语体一致、grammarPoint与标准答案相符，并确认JSON及标签合法；发现问题先修正，不输出检查过程或增加字段。
            """;

    private final ObjectMapper objectMapper;

    public AiReviewQuestionPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            UserErrorType userErrorType,
            ErrorType errorType,
            List<Question> cycleQuestions,
            Map<Long, List<QuestionAnswer>> answersByQuestionId,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
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
                请根据复习重点和本周期已有题目生成一道新的复习衍生题。

                复习重点：%s
                本周期已有题目及答案：%s
                场景标签候选：%s
                功能标签候选：%s

                JSON结构：
                {"question":{"sourceText":"中文题干","contextText":"中文语境","grammarPoint":"语法点","tagCodes":["场景标签code","可选的功能标签code"],"answers":[{"answerText":"日语答案","answerType":"STANDARD","primaryAnswer":true,"sortOrder":0}]}}
                """.formatted(
                toJson(targetError),
                toJson(existingQuestions),
                toJson(sceneTagOptions),
                toJson(functionTagOptions));
        TranslationDirection direction = TranslationDirection.fromLearningMode(userErrorType.getLearningMode());
        return new AiQuestionPrompt(direction.applyPromptRules(SYSTEM_PROMPT), direction.applyPromptRules(userPrompt));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("复习生题 Prompt JSON 序列化失败", exception);
        }
    }
}
