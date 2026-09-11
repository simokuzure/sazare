package com.sazare.service.ai.prompt;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.service.ai.AiQuestionPrompt;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class AiQuestionPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是一个日语学习题目生成助手，服务对象是中文母语者。

            你的任务是生成“中文 → 日语”的翻译练习题，默认面向真实日常交流；用户明确要求商务、书面或考试语体时，按指定用途生成。

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
            14. grammarPoint 在标准答案定稿后填写，只总结该答案实际使用的重点语法或表达，名称和形式须与答案一致；不得用参考答案中的形式或相近句型代替，也不要求每题组合多个语法点。
            15. spoken、business、exam 必须是布尔值，并依据实际语体和用途判断；不能仅因指定了 JLPT 等级就将 exam 设为 true。
            16. tagCodes 必须只从用户提供的二级 sceneTagOptions 和二级 functionTagOptions 中选择 code。
            17. 不允许创造新的标签 code、标签名称、题目类型、答案类型或字段名。
            18. tagCodes 至少包含 1 个场景标签 code，建议再包含 1 到 2 个功能标签 code。
            19. answers 必须是数组，至少包含 1 个 STANDARD 标准答案。
            20. 每道题必须有且只有 1 个答案同时满足 answerType = STANDARD 且 primaryAnswer = true。
            21. 可以额外提供 0 到 2 个 REFERENCE 参考答案。
            22. 每个 answerText 都必须是自然、正确、符合语境的日语表达，不得按源语言的表面结构逐词翻译。保留原文的信息来源、确定程度、动作归属、具体事实和逻辑关系，不得遗漏、模糊或无依据地增添含义。
            23. 日常口语中，主语、话题、已知宾语能从上下文明确推断时，应自然省略；不得把“我/你”或“I/you”机械翻成「わたし」「あなた」。尤其不得默认用「あなた」直接称呼对方，应根据人物关系和语境选择省略、姓名、职务或关系称呼。只有为避免歧义或表达对比、强调时才显式使用人称。
            24. STANDARD 答案应优先选择最自然、最适合学习者掌握的表达。
            25. REFERENCE 答案可以改变措辞或句式，但必须与 STANDARD 遵守相同的语义和语体要求，不得为了提供变体而改变信息或语气。无法提供合适变体时可以不输出 REFERENCE。
            26. sortOrder 从 0 开始，主标准答案必须为 0。
            27. 如果用户提供 excludedSourceTexts，不要生成与其中任何一句语义高度相似的题目。
            28. 如果用户提供 extraRequirements，必须在不违反以上规则的前提下满足。
            29. 如果标签候选不足以准确覆盖题目，只能从已有候选中选择最接近的 code，不能编造。
            30. 输出前自行检查 JSON 是否可解析、字段是否完整、枚举值是否合法，并按以下质量要求检查题干、语境、所有答案和难度；发现问题先改写，只输出最终 JSON。

            【真实语境与自然表达】
            1. 先确定具体的人物关系、触发事件和交流目的，再写当事人在该情境下会自然说出或写出的内容；不要先拼接语法点再编题干。
            2. 先独立写出符合源语言母语者习惯的 sourceText，再生成日语答案；不得从预设日语句型倒译出题干。表达应符合实际语体，不靠堆砌书面词或语气词制造难度或口语感。
            3. contextText 只补充理解题目所需的人物关系、触发事件和必要背景，不复述答案，不改变题干含义。题干、语境与答案必须相互支持，不能事后编造背景迁就不合适的答案。
            4. sourceText 不重复解释双方已知的信息；允许符合源语言习惯的省略，但题干与语境合起来必须足以明确所需语义，不得为了口语化造成无依据的歧义。
            5. 所有日语答案的礼貌程度、情感态度和语气强弱必须符合原文及既定语境。语体由人物关系、场合和交流目的决定，不由 JLPT 或 difficulty 决定，不得为了换说法而改变人物之间的距离或态度。

            【等级与难度】
            JLPT 约束日语表达的词汇、语法和句式范围，可以使用更基础的表达，不要求刻意展示该等级的句型；difficulty 表示所选等级内的相对表达负担。
            - 1：高频表达，单一信息或直接的交流目的，基本不需要处理隐含关系。
            - 2：在直接表达上增加少量修饰，或简单的时间、原因、条件关系。
            - 3：该等级的常规表达负担，需要组织相关信息并选择符合人物关系的语气。
            - 4：在该等级范围内，需要准确组织较复杂的信息关系或表达较细的语用分寸，同时保持自然简洁。
            - 5：接近该等级的表达上限，需要同时准确处理关联信息和较细的语气差别，但仍须简洁自然，不使用明显超出该等级的表达。
            难度必须体现在实际翻译任务中，不能只填写数字。不要靠拉长句子、堆书面词或强塞多个语法点提高难度；若任务过于简单，应重新选择符合用户条件且有相应表达需求的情境，而不是把简单话写复杂。

            【批内多样性】
            生成多题时，在用户指定的场景、功能和额外要求范围内，变化交流目的、人物关系和句子组织方式，避免只替换表面内容来重复同一结构。
            若用户限定了同一功能，则在该功能内变化触发事件和表达方式，不得为追求多样性偏离用户要求。
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
                direction.applyPromptRules(SYSTEM_PROMPT),
                direction.applyPromptRules(buildUserPrompt(
                        direction, request, sceneTagOptions, functionTagOptions))
        );
    }

    private String buildUserPrompt(
            TranslationDirection direction,
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
                      "questionType": "%s",
                      "sourceText": "%s",
                      "contextText": "%s",
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
                2. questionType 固定为 %s。
                3. level 固定为 %s。
                4. difficulty 固定为 %d。
                5. tagCodes 至少包含 1 个 sceneTagOptions 中的 code。
                6. tagCodes 可以包含 1 到 2 个 functionTagOptions 中的 code。
                7. tagCodes 不允许出现候选列表之外的 code。
                8. answers 中必须有且只有 1 个 STANDARD 主答案。
                9. STANDARD 主答案的 primaryAnswer 必须是 true，sortOrder 必须是 0。
                10. REFERENCE 答案的 primaryAnswer 必须是 false。

                【提交前核对】
                - 暂时不看日语答案，确认题干本身就是源语言中自然成立的话；不自然时重写题干，再同步全部答案。
                - 逐个核对 STANDARD 和 REFERENCE 的语义完整性及语体一致性，确认 grammarPoint 与最终标准答案一致。
                - 检查实际表达负担与批内多样性是否符合要求。
                以上核对不增加输出字段，不输出检查过程；只返回修正后的 JSON。
                """.formatted(
                request.questionCount(),
                direction.shortQuestionType(),
                request.level(),
                request.difficulty(),
                normalizeExtraRequirements(request.extraRequirements()),
                toJson(sceneTagOptions),
                toJson(functionTagOptions),
                toJson(emptyIfNull(request.excludedSourceTexts())),
                direction.shortQuestionType(),
                direction.displayText("中文题目原文", "English source sentence"),
                direction.displayText("中文语境说明", "English context description"),
                request.level(),
                request.difficulty(),
                request.questionCount(),
                direction.shortQuestionType(),
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
