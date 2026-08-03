package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.service.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class AiAnswerScoringPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是面向中文母语者的日语翻译评分助手。
            只评估 TRANSLATION_ZH_TO_JA 类型题目，并仅输出一个合法 JSON 对象，不要输出 Markdown 或额外说明。
            评分应综合题目、语境、参考答案和用户答案。不能仅因表达与参考答案不同就判错。
            grammarPoint 仅是学习参考，不是必须使用的句型或固定表达；不得仅因用户答案没有使用 grammarPoint 而扣分、列为错误或降低信息完整性。只要替代表达在语法、语义、语用和场景上自然且达意，即应认可。
            日常口语中自然省略的主语、话题、已知宾语，以及随省略成分一同不出现的助词，不得列为漏译或助词错误。只有省略导致关键信息缺失、语义歧义、语气或场景不成立时，才可判定为漏译，并在 issue 中说明具体影响。
            errorAnalysis 只列出有明确依据的错误；没有错误时返回空数组。
            每个 original 必须是用户答案中连续出现的原文片段；不要推测未出现的错误，也不要重复相同 errorTypeCode 与 original 的组合。
            errorTypeCode 必须严格从输入 errorTypeOptions 中选择二级分类编码，不能使用一级分类或自行创造编码。
            issue、suggestion、suggestedUserErrorTypeName、suggestedUserErrorTypeDescription 使用简洁中文。
            suggestedUserErrorTypeName 必须概括可在多次作答中复现的具体错误模式，包含关键语义/语法对象和误用方向或适用场景；不得使用“助词错误”“词汇错误”“句型错误”“不自然表达”等泛称。description 应说明触发情形、错误形式与正确用法。
            severity 只能是 LOW、MEDIUM、HIGH；recommendedExpressions.formality 只能是 CASUAL、NEUTRAL、POLITE、BUSINESS。
            scores 中四个维度均为 0 到 100 的整数；totalScore 是四个维度平均值，保留两位小数。
            """;

    private final ObjectMapper objectMapper;

    public AiAnswerScoringPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions,
            List<AiErrorTypeOptionDTO> errorTypeOptions,
            AiAnswerScoringRequest request
    ) {
        return new AiQuestionPrompt(
                SYSTEM_PROMPT,
                buildUserPrompt(question, standardAnswers, tagOptions, errorTypeOptions, request)
        );
    }

    private String buildUserPrompt(
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions,
            List<AiErrorTypeOptionDTO> errorTypeOptions,
            AiAnswerScoringRequest request
    ) {
        return """
                请评分以下日语翻译作答，并按指定 JSON 结构返回。

                题目信息：
                %s

                题目标签：
                %s

                参考答案：
                %s

                可选错误类型（只能使用其中的 code）：
                %s

                用户答案：
                %s

                JSON 结构：
                {
                  "review": {
                    "scores": {
                      "grammarVocabularyScore": 0,
                      "naturalFluencyScore": 0,
                      "scenarioAdaptationScore": 0,
                      "informationCompletenessScore": 0
                    },
                    "totalScore": 0.00,
                    "overallComment": "中文总评",
                    "comments": {
                      "grammarComment": "中文语法说明",
                      "vocabularyComment": "中文词汇说明",
                      "naturalnessComment": "中文自然度说明",
                      "scenarioComment": "中文场景说明"
                    },
                    "errorAnalysis": [
                      {
                        "errorTypeCode": "<errorTypeOptions 中的 code>",
                        "original": "用户答案中的片段",
                        "issue": "中文问题说明",
                        "suggestion": "中文修改建议",
                        "severity": "MEDIUM",
                        "suggestedUserErrorTypeName": "具体错误类型名称，例如：赶上交通工具时误用を而非に",
                        "suggestedUserErrorTypeDescription": "触发情形、错误形式与正确用法，例如：表示赶上交通工具时使用「交通工具に間に合う」，不能说「交通工具を間に合う」。"
                      }
                    ],
                    "revisionSuggestions": ["中文修改建议"],
                    "recommendedExpressions": [
                      {
                        "expression": "日语表达",
                        "usage": "中文使用场景",
                        "formality": "POLITE",
                        "note": "中文说明"
                      }
                    ]
                  }
                }
                """.formatted(
                toJson(Map.of(
                        "questionType", question.getQuestionType(),
                        "sourceText", question.getSourceText(),
                        "contextText", question.getContextText(),
                        "level", question.getLevel(),
                        "difficulty", question.getDifficulty(),
                        "grammarPoint", question.getGrammarPoint(),
                        "spoken", question.getSpoken(),
                        "business", question.getBusiness(),
                        "exam", question.getExam()
                )),
                toJson(tagOptions),
                toJson(toAnswerPromptValues(standardAnswers)),
                toJson(errorTypeOptions),
                request.answerText().trim()
        );
    }

    private List<Map<String, Object>> toAnswerPromptValues(List<QuestionAnswer> answers) {
        return answers.stream()
                .map(answer -> Map.<String, Object>of(
                        "answerText", answer.getAnswerText(),
                        "answerType", answer.getAnswerType(),
                        "primaryAnswer", answer.getPrimaryAnswer(),
                        "sortOrder", answer.getSortOrder()
                ))
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Prompt JSON 序列化失败", exception);
        }
    }
}
