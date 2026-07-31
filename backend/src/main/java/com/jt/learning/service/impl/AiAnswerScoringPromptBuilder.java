package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerScoringRequest;
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
            你是一个日语学习评分助手，服务对象是中文母语者。

            你的任务是根据中文原文、语境、标准答案和用户提交的日语答案，对“中文 → 日语”翻译练习进行评分和纠错。

            必须严格遵守以下规则：

            1. 只处理题目类型为 TRANSLATION_ZH_TO_JA 的中译日答案。
            2. 只返回合法 JSON，不要返回 Markdown、代码块标记、解释文字、注释或多余前后缀。
            3. JSON 顶层必须是一个对象，且只包含 review 字段。
            4. review 必须包含 scores、totalScore、overallComment、comments、errorAnalysis、revisionSuggestions、recommendedExpressions 字段。
            5. 所有分数必须是 0 到 100 的整数。
            6. scores 必须包含 grammarVocabularyScore、naturalFluencyScore、scenarioAdaptationScore、informationCompletenessScore。
            7. totalScore 必须等于四项评分的算术平均值，保留 2 位小数。
            8. 评分必须以中文原文含义、语境、JLPT 等级、难度、标准答案和用户答案为依据。
            9. 不要求用户答案与标准答案完全一致；语义正确、自然且符合语境的表达可以得高分。
            10. 如果用户答案语义正确但表达不够自然，应在自然度或场景适配上扣分。
            11. 如果用户答案语义缺失、添加了原文没有的信息或误解原文，应在表达完整性上扣分。
            12. 如果敬语、语气、商务/口语场景不匹配，应在场景适配上扣分。
            13. 错误分析必须具体指出问题，不要只写“有语法错误”这类空泛描述。
            14. 修改建议应给出可执行的改法。
            15. 推荐表达必须是自然日语，可以包含标准答案或更适合语境的表达。
            16. 不要编造题目中不存在的背景信息。
            17. 不要输出 JSON 契约之外的字段。
            18. 输出前自行检查 JSON 是否可解析、字段是否完整、分数是否在范围内。
            """;

    private final ObjectMapper objectMapper;

    public AiAnswerScoringPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions,
            AiAnswerScoringRequest request
    ) {
        return new AiQuestionPrompt(SYSTEM_PROMPT, buildUserPrompt(question, standardAnswers, tagOptions, request));
    }

    private String buildUserPrompt(
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions,
            AiAnswerScoringRequest request
    ) {
        return """
                请评分下面这道中译日练习。

                【题目信息】
                - 题目类型：%s
                - 中文原文：%s
                - 语境说明：%s
                - JLPT 等级：%s
                - 难度：%d
                - 语法点：%s
                - 是否口语：%s
                - 是否商务：%s
                - 是否考试：%s

                【题目标签】
                %s

                【标准答案和参考答案】
                %s

                【用户答案】
                %s

                【输出要求】
                只返回合法 JSON。
                不要返回 Markdown。
                不要使用代码块。
                不要添加解释。
                不要添加 JSON 之外的任何文字。

                【JSON 结构】
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
                      "grammarComment": "中文语法评价",
                      "vocabularyComment": "中文词汇评价",
                      "naturalnessComment": "中文自然度评价",
                      "scenarioComment": "中文敬语与场景适配评价"
                    },
                    "errorAnalysis": [
                      {
                        "type": "GRAMMAR",
                        "original": "用户答案中的问题片段",
                        "issue": "中文说明具体问题",
                        "suggestion": "中文说明如何修改",
                        "severity": "MEDIUM"
                      }
                    ],
                    "revisionSuggestions": [
                      "中文修改建议"
                    ],
                    "recommendedExpressions": [
                      {
                        "expression": "自然日语表达",
                        "usage": "中文说明适用场景",
                        "formality": "POLITE",
                        "note": "中文补充说明"
                      }
                    ]
                  }
                }

                【枚举规则】
                1. errorAnalysis.type 只能是 GRAMMAR、VOCABULARY、NATURALNESS、HONORIFIC、SCENARIO、COMPLETENESS。
                2. errorAnalysis.severity 只能是 LOW、MEDIUM、HIGH。
                3. recommendedExpressions.formality 只能是 CASUAL、NEUTRAL、POLITE、BUSINESS。
                """.formatted(
                question.getQuestionType(),
                question.getSourceText(),
                question.getContextText(),
                question.getLevel(),
                question.getDifficulty(),
                question.getGrammarPoint(),
                question.getSpoken(),
                question.getBusiness(),
                question.getExam(),
                toJson(tagOptions),
                toJson(toAnswerPromptValues(standardAnswers)),
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
