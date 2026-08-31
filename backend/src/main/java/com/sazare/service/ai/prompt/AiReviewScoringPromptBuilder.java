package com.sazare.service.ai.prompt;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.entity.ErrorType;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.UserErrorType;
import com.sazare.service.ai.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class AiReviewScoringPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是日语复习卡片评分助手。只输出一个合法 JSON 对象，不要输出 Markdown 或额外说明。
            核心任务是判断用户是否掌握指定的复习重点，而不是要求答案与参考答案逐字一致。
            自然且满足复习重点的等价表达可以接受。
            quality 只能是0到5的整数：0=空白或无关；1=完全没有掌握复习重点；2=仍未掌握但部分可用；
            3=已掌握复习重点但有较明显的其他问题；4=已掌握且仅有轻微问题；5=正确、自然且符合场景。
            quality为0到2时targetErrorResolved必须为false；quality为3到5时必须为true。
            scores包含语法与词汇、自然流畅度、场景适配度、信息完整性四项评分，每项只能是0到100的整数。
            scores评价答案整体质量，与用于判断复习重点是否掌握的quality分别独立判断。
            feedback使用简洁中文，说明复习重点是否掌握及最重要的改进点。
            errorAnalysis只记录有明确依据的候选新错误；original必须是用户答案中的连续片段。
            errorTypeCode必须来自输入的errorTypeOptions，severity只能是LOW、MEDIUM、HIGH。
            同一errorTypeCode与original组合不得重复。建议的复习重点名称应具体、可复现，不能使用宽泛分类名。
            """;

    private final ObjectMapper objectMapper;

    public AiReviewScoringPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            UserErrorType userErrorType,
            ErrorType errorType,
            Question question,
            List<QuestionAnswer> answers,
            List<AiErrorTypeOptionDTO> errorTypeOptions,
            String answerText
    ) {
        String userPrompt = """
                请评估本次复习作答。

                复习重点：%s
                题目信息：%s
                参考答案：%s
                可选错误类型：%s
                用户答案：%s

                JSON结构：
                {"review":{"quality":0,"targetErrorResolved":false,"feedback":"中文反馈","scores":{"grammarVocabularyScore":0,"naturalFluencyScore":0,"scenarioAdaptationScore":0,"informationCompletenessScore":0},"errorAnalysis":[{"errorTypeCode":"可选code","original":"用户答案片段","issue":"中文说明","suggestion":"中文建议","severity":"MEDIUM","suggestedUserErrorTypeName":"具体复习重点","suggestedUserErrorTypeDescription":"适用情形、需改进的表达和推荐用法"}]}}
                """.formatted(
                toJson(Map.of(
                        "userErrorTypeName", userErrorType.getName(),
                        "userErrorTypeDescription", userErrorType.getDescription(),
                        "errorTypeCode", errorType.getCode(),
                        "errorTypeName", errorType.getName()
                )),
                toJson(question),
                toJson(answers),
                toJson(errorTypeOptions),
                answerText.trim()
        );
        TranslationDirection direction = TranslationDirection.fromLearningMode(userErrorType.getLearningMode());
        return new AiQuestionPrompt(direction.applyPromptRules(SYSTEM_PROMPT), direction.applyPromptRules(userPrompt));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("复习评分 Prompt JSON 序列化失败", exception);
        }
    }
}
