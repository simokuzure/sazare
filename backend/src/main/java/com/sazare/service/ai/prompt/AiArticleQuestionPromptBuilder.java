package com.sazare.service.ai.prompt;

import com.sazare.common.ArticleLengthTier;
import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiArticleRetryContext;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class AiArticleQuestionPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是面向中文母语者的日语文章翻译练习题生成助手。
            直接原创一篇自然中文文章，并为每个中文句子提供忠实、自然的日语参考译文。
            不得复现、引用或声称改写真实文章、作者或出处。
            只输出合法 JSON 对象，不要输出 Markdown、代码块或额外说明。
            源文章长度必须严格符合用户条件中的 articleLength；输出前自行计数并改写到指定范围，不得自行扩大或缩小。
            JLPT 与 difficulty 只决定怎样表达，不决定写什么：不得据此限制、替换、弱化或回避主题、体裁、情节、人物、冲突、观点或抽象议题。
            topic、genre、blueprint 和 extraRequirements 决定文章内容，必须完整保留；即使 JLPT 较低，也应使用较简单的词汇、语法和句式表达同样的内容。
            源文章的句法负担和 japaneseReference 的日语词汇、语法、句式都必须符合 languageRequirements。
            JLPT 是语言能力上限：
            - N5 只使用 N5 范围内的日语词汇和语法，减少长修饰与复杂从句。
            - N4 可以使用 N5 到 N4 范围内更丰富的词汇、语法和句式。
            - N3 可以使用 N5 到 N3 范围内更丰富的词汇、语法和句式。
            - N2 可以使用 N5 到 N2 范围内更丰富的词汇、语法和句式。
            - N1 可以使用 N5 到 N1 范围内的词汇、语法和句式。
            difficulty 只细分所选 JLPT 内的语言形式，不得突破 JLPT 上限：1 使用高频词、短句和直接表达；2 使用少量修饰和简单从句；3 使用该等级常规表达；4 使用更多复句、同级较低频词和篇章连接；5 接近该等级上限。
            sentences 按中文句子拆分；每项只能包含一个不换行的完整句子，索引从 0 连续递增。
            chineseText 必须包含中文，不得包含平假名或片假名，并以。？！之一结束。
            japaneseReference 必须包含平假名或片假名，不得换行，并忠实对应 chineseText。
            日语参考句不得重复。
            contextText 只描述文章背景、体裁和语体，不得包含“AI 原创”等题目来源信息。
            grammarPoint 字段用于生词提示，不是语法点。选择文章中相对当前 JLPT 等级较难的 3 到 6 个词语和专用名称，提供对应日语；每行格式为“中文词语：日语表达（读音）”，总长度不超过 255 个字符。
            生词提示只列词语或短语，不得提供完整句子的日语翻译；相同词语不得重复。专用名称出现时应优先提示。
            每次必须先根据 blueprintSeed 和当前 GENRE 生成一个 coreConcept 及四个指定语义角色，再检查角色之间是否相关、是否矛盾；发现冲突时先修正蓝图，再依据最终蓝图写文章。
            蓝图种子只是发散线索，不得直接出现在文章中。blueprint.seed 必须原样返回 blueprintSeed。
            真实性与写作风格要求：
            1. 不要把文章写成安全、正确、面面俱到的示范材料。可以大胆选择反常但合理的切入点，允许尖锐冲突、尴尬、偏见、私心、荒诞感、道德暧昧和不讨喜的人物，但内容必须自洽并符合指定体裁。
            2. 涉及人物时，人物不能只是传递信息或推动剧情的工具。优先通过具体动作、停顿、答非所问、口头习惯、错误判断、隐瞒和潜台词表现人物；不同人物的说话方式应有可辨识的差异。
            3. 不要直接宣布“他很悲伤”“她充满失望”等情绪，也不要用总结性旁白替读者解释一切。优先选择能让读者自行感受到情绪的动作、物件、声音或不完整表达。
            4. 不必交代完整起因、经过和结局。可以从事件中途切入，在矛盾尚未解决、人物没有达成共识或某个动作尚未完成时结束。
            5. 蓝图只确定起始处境、人物欲望、观察角度或待处理的问题，不得提前写死最终成功、失败、和解、分手、感悟或解决方案；即使角色名称包含 goal、change、insight，也只能描述期待或可能方向。
            6. 避免“小明、小美、小强”等占位式姓名；姓名不重要时使用自然的身份或关系称呼，姓名重要时选择符合背景但不过度刻意的名字。
            7. 避免格言、鸡汤、升华、标准答案式结论和“这件事让我明白了……”之类收束。不要为了积极正面而消解真实矛盾。
            8. 从以上技巧中选择适合本次体裁和蓝图的部分自然运用，不要机械地逐项满足，也不要为了大胆而堆砌猎奇元素。
            9. 当 GENRE 为 DIALOGUE 或正文包含连续对话时，不得连续套用“姓名＋动作＋说/问/回答/反驳＋台词”的整齐句式，也不要让双方机械地一人一句轮流交换信息。说话顺序清楚时应省略重复的人名和说话动词，自然混用独立台词、必要的动作片段、短暂沉默、打断、回避和没有被正面回答的问题。
            10. 对话中的动作必须改变现场关系、暴露真实态度或提供关键信息；不要为了显得生动而给每句话装饰“端起茶杯、放下杯子、看向窗外、叹了一口气”等无实际作用的动作。人物可以说得含糊、说错、改口或只说半句，不必替读者把背景和立场解释完整。
            禁止套用或仅替换人物、地点、物品来改写以下常见骨架：
            1. 周末与朋友出游—发现小店或风景—愉快结束。
            2. 下雨、迷路或交通受阻—陌生人帮助—感到温暖。
            3. 初到学校或职场紧张—同伴帮助—顺利适应。
            4. 丢失物品—寻找或被归还—感谢与反思。
            5. 初次尝试失败—反复练习—最终成功。
            6. 亲友产生误会—坦诚沟通—关系更加牢固。
            7. 工作学习压力—调整计划—完成任务并领悟时间管理。
            8. 看到旧物或返回故乡—回忆往事—珍惜当下。
            9. 以“随着社会发展”开头—罗列利弊—笼统呼吁行动。
            10. 简单介绍对象—固定罗列优缺点—无条件推荐
            小说可以是完整故事或片段，也允许开放状态、未解决冲突和非圆满结尾。
            """;

    private final ObjectMapper objectMapper;

    public AiArticleQuestionPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            AiArticleGenerationRequest request,
            AiQuestionTagOptionDTO genreTag,
            String seed,
            AiArticleRetryContext retryContext
    ) {
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        ArticleLengthTier lengthTier = ArticleLengthTier.from(request.lengthTier());
        Map<String, String> semanticRoleDefinitions = ArticleGenreRoleRegistry.rolesFor(genreTag.code());
        Map<String, String> responseRoleExample = new LinkedHashMap<>();
        semanticRoleDefinitions.forEach((key, description) -> responseRoleExample.put(key, description + "短语"));

        Map<String, Object> articleLength = new LinkedHashMap<>();
        articleLength.put("tier", lengthTier.name());
        articleLength.put("minimum", lengthTier.minimum(direction));
        articleLength.put("maximum", lengthTier.maximum(direction));
        articleLength.put("unit", direction.articleLengthUnit());

        Map<String, Object> conditions = new LinkedHashMap<>();
        conditions.put("level", request.level());
        conditions.put("difficulty", request.difficulty());
        conditions.put("articleLength", articleLength);
        conditions.put("languageRequirements", Map.of(
                "jlpt", jlptRequirement(request.level()),
                "difficulty", difficultyRequirement(request.difficulty()),
                "contentRule", "保持主题、体裁、情节、人物、冲突、观点和抽象程度不变，只调整语言表达难度"
        ));
        conditions.put("genre", genreTag);
        conditions.put("topic", request.topic() == null ? "不限" : request.topic());
        conditions.put("extraRequirements", request.extraRequirements() == null ? "无" : request.extraRequirements());
        conditions.put("blueprintSeed", seed);
        conditions.put("semanticRoleDefinitions", semanticRoleDefinitions);

        String retryInstructions = retryContext == null
                ? "这是首次尝试，没有历史拒绝信息。"
                : """
                上一篇文章因正文语义重复被拒绝。必须依据新的 blueprintSeed 生成实质不同的蓝图和文章，不能只替换地名、人物、物品或近义词。
                拒绝信息：
                %s
                """.formatted(toJson(retryContext));
        String userPrompt = """
                请按以下条件生成一篇中文文章中译日练习题：
                %s

                %s

                blueprint.roles 必须且只能包含 semanticRoleDefinitions 中的四个英文键；各值用非空中文短语概括。
                coreConcept 使用简洁的中文短语，文章内容必须落实最终蓝图。蓝图应保留发展空间，不得概括完整剧情或预先给出结局。

                JSON 结构：
                {
                  "blueprint": {
                    "seed": "%s",
                    "coreConcept": "核心概念短语",
                    "roles": %s
                  },
                  "article": {
                    "questionType": "%s",
                    "contextText": "%s",
                    "level": "%s",
                    "difficulty": %d,
                    "grammarPoint": "%s",
                    "spoken": false,
                    "business": false,
                    "exam": false,
                    "sentences": [
                      {
                        "index": 0,
                        "chineseText": "%s",
                        "japaneseReference": "対応する自然な日本語文。"
                      }
                    ]
                  }
                }
                """.formatted(
                toJson(conditions),
                retryInstructions,
                seed,
                toJson(responseRoleExample),
                direction.articleQuestionType(),
                direction.displayText("中文背景、体裁和语体说明", "English background, genre, and register"),
                request.level(),
                request.difficulty(),
                direction.displayText(
                        "郊外：郊外（こうがい）\\n专用名称：对应日语名称（读音）",
                        "suburb: 郊外（こうがい）\\nproper noun: corresponding Japanese name (reading)"
                ),
                direction.displayText("完整中文句子。", "A complete English sentence.")
        );
        return new AiQuestionPrompt(direction.applyPromptRules(SYSTEM_PROMPT), direction.applyPromptRules(userPrompt));
    }

    private String jlptRequirement(String level) {
        return switch (level) {
            case "N5" -> "只使用 N5 范围内的日语词汇和语法，源文使用短而清楚的句式，减少长修饰与复杂从句";
            case "N4" -> "使用 N5 到 N4 范围内的日语词汇和语法，允许较简单的修饰、原因、条件和顺序表达";
            case "N3" -> "使用 N5 到 N3 范围内的日语词汇和语法，允许中等长度复句和常用篇章连接";
            case "N2" -> "使用 N5 到 N2 范围内的日语词汇和语法，允许较复杂复句和书面表达";
            case "N1" -> "可以使用 N5 到 N1 范围内的日语词汇、语法和句式";
            default -> throw new IllegalArgumentException("不支持的 JLPT 等级：" + level);
        };
    }

    private String difficultyRequirement(Integer difficulty) {
        return switch (difficulty) {
            case 1 -> "同级内使用高频词、短句和直接表达，不依赖隐含关系";
            case 2 -> "同级内允许少量修饰和简单从句";
            case 3 -> "使用所选 JLPT 等级的常规表达复杂度";
            case 4 -> "同级内使用更多复句、较低频词和篇章连接，可包含少量隐含关系";
            case 5 -> "接近所选 JLPT 等级的表达上限，但不得使用明显超纲的词汇或语法";
            default -> throw new IllegalArgumentException("不支持的难度：" + difficulty);
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("文章生成 Prompt JSON 序列化失败", exception);
        }
    }
}
