package com.jt.learning.service.ai.prompt;

import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.service.ai.AiQuestionPrompt;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

public class AiArticleQuestionPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是面向中文母语者的日语文章翻译练习题生成助手。
            直接原创一篇自然中文文章，并为每个中文句子提供忠实、自然的日语参考译文。
            不得复现、引用或声称改写真实文章、作者或出处。
            只输出合法 JSON 对象，不要输出 Markdown、代码块或额外说明。
            中文文章去除空白后必须为 150 到 300 个 Unicode 字符。
            sentences 按中文句子拆分；每项只能包含一个不换行的完整句子，索引从 0 连续递增。
            chineseText 必须包含中文，不得包含平假名或片假名，并以。？！之一结束。
            japaneseReference 必须包含平假名或片假名，不得换行，并忠实对应 chineseText。
            日语参考句不得重复。
            """;

    private final ObjectMapper objectMapper;

    public AiArticleQuestionPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AiQuestionPrompt build(
            AiArticleGenerationRequest request,
            AiQuestionTagOptionDTO genreTag
    ) {
        String userPrompt = """
                请按以下条件生成一篇中文文章中译日练习题：
                %s

                JSON 结构：
                {
                  "article": {
                    "questionType": "TRANSLATION_ZH_TO_JA_ARTICLE",
                    "contextText": "中文背景、体裁和语体说明",
                    "level": "%s",
                    "difficulty": %d,
                    "grammarPoint": "文章翻译重点",
                    "spoken": false,
                    "business": false,
                    "exam": false,
                    "sentences": [
                      {
                        "index": 0,
                        "chineseText": "完整中文句子。",
                        "japaneseReference": "対応する自然な日本語文。"
                      }
                    ]
                  }
                }
                """.formatted(
                toJson(Map.of(
                        "level", request.level(),
                        "difficulty", request.difficulty(),
                        "genre", genreTag,
                        "topic", request.topic() == null ? "不限" : request.topic(),
                        "extraRequirements", request.extraRequirements() == null ? "无" : request.extraRequirements()
                )),
                request.level(),
                request.difficulty()
        );
        return new AiQuestionPrompt(SYSTEM_PROMPT, userPrompt);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("文章生成 Prompt JSON 序列化失败", exception);
        }
    }
}
