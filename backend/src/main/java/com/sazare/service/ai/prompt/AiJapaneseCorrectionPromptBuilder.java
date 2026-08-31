package com.sazare.service.ai.prompt;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.service.ai.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class AiJapaneseCorrectionPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是面向中文母语者的日语纠错助手，只检查用户提供的日语文本，并且只输出一个合法 JSON 对象。
            没有中文原文或参考答案，不得执行翻译评分，不得判断漏译、误译、过度发挥或中文直译。
            只能依据日语文本本身判断语法、词汇、自然度、篇章衔接、语体一致性、表记和输入完整性。
            不检查标点，不因标点问题扣分，不输出标点相关的错误或修改建议，也不得仅为调整标点而改写 correctedText。
            四项评分均为 0 到 100 的整数：grammarVocabularyScore 评语法与词汇准确性，naturalFluencyScore 评自然度与篇章连贯，scenarioAdaptationScore 评语体与风格一致性，informationCompletenessScore 评表记与输入完整性。
            totalScore 是四项平均值，保留两位小数。correctedText 必须在保留原意的前提下给出完整修订文本，避免无必要改写。
            errorAnalysis 只列有明确依据的错误；original 必须从用户原文中连续复制，suggestion 必须是 correctedText 中连续出现的完整日语修订句。
            reviewSourceText 必须是 suggestion 对应的简体中文含义，供后续中译日复习使用，不能包含日语假名。
            errorTypeCode 只能从输入的可选二级错误类型中选择；没有明确错误时返回空数组。
            issue、分项评语、总评、修改建议和推荐表达说明使用简洁中文。
            severity 只能是 LOW、MEDIUM、HIGH；recommendedExpressions.formality 只能是 CASUAL、NEUTRAL、POLITE、BUSINESS。
            """;

    private final ObjectMapper objectMapper;

    public AiJapaneseCorrectionPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            List<AiErrorTypeOptionDTO> errorTypeOptions,
            JapaneseCorrectionRequest request
    ) {
        String userPrompt = """
                请纠正以下日语文本，并按指定 JSON 结构返回。

                可选错误类型：%s
                用户日语原文：%s

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
                    "correctedText": "完整修订后的日语文本",
                    "overallComment": "中文总评",
                    "comments": {
                      "grammarVocabularyComment": "中文语法与词汇说明",
                      "naturalFluencyComment": "中文自然度与篇章说明",
                      "styleConsistencyComment": "中文语体与风格说明",
                      "writingCompletenessComment": "中文表记与输入完整性说明"
                    },
                    "errorAnalysis": [
                      {
                        "errorTypeCode": "<可选错误类型中的二级 code>",
                        "original": "用户原文中的连续片段",
                        "issue": "中文问题说明",
                        "suggestion": "correctedText 中的完整日语修订句",
                        "reviewSourceText": "修订句对应的简体中文含义",
                        "severity": "MEDIUM",
                        "suggestedUserErrorTypeName": "可反复练习的具体复习重点",
                        "suggestedUserErrorTypeDescription": "触发情形、错误形式与正确用法"
                      }
                    ],
                    "revisionSuggestions": ["中文修改建议"],
                    "recommendedExpressions": [
                      {
                        "expression": "日语推荐表达",
                        "usage": "中文使用场景",
                        "formality": "POLITE",
                        "note": "中文说明"
                      }
                    ]
                  }
                }
                没有明确错误时 errorAnalysis 必须返回 []；没有推荐表达时 recommendedExpressions 必须返回 []。
                """.formatted(toJson(errorTypeOptions), request.japaneseText().trim());
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        return new AiQuestionPrompt(direction.applyPromptRules(SYSTEM_PROMPT), direction.applyPromptRules(userPrompt));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("日语纠错 Prompt JSON 序列化失败", exception);
        }
    }
}
