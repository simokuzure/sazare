package com.jt.learning.service.ai.prompt;

import com.jt.learning.common.TranslationDirection;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.service.ai.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class AiQuestionPromptBuilder {

    private static final String QUESTION_TYPE = "TRANSLATION_ZH_TO_JA";

    private static final String SYSTEM_PROMPT = """
            你是一个日语学习题目生成助手，服务对象是中文母语者。

            你的任务是生成“中文 → 日语”的翻译练习题。

            必须严格遵守以下规则：

            1. 只生成题目类型为 TRANSLATION_ZH_TO_JA 的题目。
            2. 只返回合法 JSON，不要返回 Markdown、代码块标记、解释文字、注释或多余前后缀。
            3. JSON 顶层必须是一个对象，且只包含 questions 字段。
            4. questions 必须是数组，数组长度必须等于用户要求的题目数量。
            5. 每道题必须包含 questionType、sourceText、contextText、level、difficulty、grammarPoint、spoken、business、exam、tagCodes、answers。
            6. questionType 必须固定为 TRANSLATION_ZH_TO_JA。
            7. sourceText 必须是自然中文句子，适合作为中译日练习题。
            8. sourceText 不要过长，N5/N4 建议 10 到 25 个汉字，N3/N2/N1 可以适当更长。
            9. sourceText 不能包含日语原文、日语假名或明显提示答案的内容。
            10. contextText 必须用中文描述题目的使用场景，帮助学习者理解语境。
            11. level 只能使用用户指定的 JLPT 等级，不能自行更改。
            12. difficulty 只能使用用户指定的难度，不能自行更改。
            13. difficulty 必须是 1 到 5 的整数。
            14. grammarPoint 用中文或日语简短说明本题重点语法或表达。
            15. spoken、business、exam 必须是布尔值。
            16. tagCodes 必须只从用户提供的 sceneTagOptions 和 functionTagOptions 中选择 code。
            17. 不允许创造新的标签 code、标签名称、题目类型、答案类型或字段名。
            18. tagCodes 至少包含 1 个场景标签 code，建议再包含 1 到 2 个功能标签 code。
            19. answers 必须是数组，至少包含 1 个 STANDARD 标准答案。
            20. 每道题必须有且只有 1 个答案同时满足 answerType = STANDARD 且 primaryAnswer = true。
            21. 可以额外提供 0 到 2 个 REFERENCE 参考答案。
            22. answerText 必须是自然、正确、符合语境的日语表达。
            23. STANDARD 答案应优先选择最自然、最适合学习者掌握的表达。
            24. REFERENCE 答案可以提供语义相近的自然表达，但不能明显偏离 sourceText。
            25. sortOrder 从 0 开始，主标准答案必须为 0。
            26. 如果用户提供 excludedSourceTexts，不要生成与其中任何一句语义高度相似的题目。
            27. 如果用户提供 extraRequirements，必须在不违反以上规则的前提下满足。
            28. 如果标签候选不足以准确覆盖题目，只能从已有候选中选择最接近的 code，不能编造。
            29. 输出前自行检查 JSON 是否可解析、字段是否完整、枚举值是否合法。
            """;

    private final ObjectMapper objectMapper;

    public AiQuestionPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        return new AiQuestionPrompt(
                direction.adaptPrompt(SYSTEM_PROMPT),
                direction.adaptPrompt(buildUserPrompt(request, sceneTagOptions, functionTagOptions))
        );
    }

    private String buildUserPrompt(
            AiQuestionGenerationRequest request,
            List<AiQuestionTagOptionDTO> sceneTagOptions,
            List<AiQuestionTagOptionDTO> functionTagOptions
    ) {
        return """
                请生成日语中译日练习题。

                【生成条件】
                - 题目数量：%d
                - 题目类型：%s
                - JLPT 等级：%s
                - 难度：%d
                - 额外要求：%s

                【可选场景标签】
                只能从下面的 sceneTagOptions 中选择场景标签 code：
                %s

                【可选功能标签】
                只能从下面的 functionTagOptions 中选择功能标签 code：
                %s

                【需要避免重复的中文题目】
                不要生成与下面任意一句语义高度相似的题目：
                %s

                【输出要求】
                只返回合法 JSON。
                不要返回 Markdown。
                不要使用代码块。
                不要添加解释。
                不要添加 JSON 之外的任何文字。

                【JSON 结构】
                {
                  "questions": [
                    {
                      "questionType": "TRANSLATION_ZH_TO_JA",
                      "sourceText": "中文题目原文",
                      "contextText": "中文语境说明",
                      "level": "%s",
                      "difficulty": %d,
                      "grammarPoint": "本题重点语法或表达",
                      "spoken": true,
                      "business": false,
                      "exam": false,
                      "tagCodes": ["只能使用上方标签候选中的code"],
                      "answers": [
                        {
                          "answerText": "日语标准答案",
                          "answerType": "STANDARD",
                          "primaryAnswer": true,
                          "sortOrder": 0
                        }
                      ]
                    }
                  ]
                }

                【字段规则】
                1. questions 数组长度必须等于 %d。
                2. questionType 固定为 TRANSLATION_ZH_TO_JA。
                3. level 固定为 %s。
                4. difficulty 固定为 %d。
                5. tagCodes 至少包含 1 个 sceneTagOptions 中的 code。
                6. tagCodes 可以包含 1 到 2 个 functionTagOptions 中的 code。
                7. tagCodes 不允许出现候选列表之外的 code。
                8. answers 中必须有且只有 1 个 STANDARD 主答案。
                9. STANDARD 主答案的 primaryAnswer 必须是 true，sortOrder 必须是 0。
                10. REFERENCE 答案的 primaryAnswer 必须是 false。
                """.formatted(
                request.questionCount(),
                QUESTION_TYPE,
                request.level(),
                request.difficulty(),
                normalizeExtraRequirements(request.extraRequirements()),
                toJson(sceneTagOptions),
                toJson(functionTagOptions),
                toJson(emptyIfNull(request.excludedSourceTexts())),
                request.level(),
                request.difficulty(),
                request.questionCount(),
                request.level(),
                request.difficulty()
        );
    }

    private String normalizeExtraRequirements(String extraRequirements) {
        if (extraRequirements == null || extraRequirements.isBlank()) {
            return "无";
        }
        return extraRequirements.trim();
    }

    private List<String> emptyIfNull(List<String> sourceTexts) {
        return sourceTexts == null ? List.of() : sourceTexts;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Prompt JSON 序列化失败", exception);
        }
    }
}
