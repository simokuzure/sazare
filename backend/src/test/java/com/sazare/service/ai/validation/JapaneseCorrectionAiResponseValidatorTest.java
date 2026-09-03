package com.sazare.service.ai.validation;

import com.sazare.dto.AiErrorTypeOptionDTO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JapaneseCorrectionAiResponseValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JapaneseCorrectionAiResponseValidator validator = new JapaneseCorrectionAiResponseValidator(
            objectMapper,
            new AiErrorAnalysisValidator()
    );

    @Test
    void shouldValidateCorrectionWithoutArticleSourceOrReferenceFields() {
        String response = """
                {
                  "review": {
                    "scores": {
                      "grammarVocabularyScore": 80,
                      "naturalFluencyScore": 81,
                      "scenarioAdaptationScore": 82,
                      "informationCompletenessScore": 83
                    },
                    "totalScore": 0,
                    "correctedText": "私は昨日、図書館へ行きました。",
                    "overallComment": "修订后表达自然。",
                    "comments": {
                      "grammarVocabularyComment": "助词已修正。",
                      "naturalFluencyComment": "表达连贯。",
                      "styleConsistencyComment": "敬体一致。",
                      "writingCompletenessComment": "表记完整。"
                    },
                    "errorAnalysis": [{
                      "errorTypeCode": "PARTICLE",
                      "original": "図書館を行きました",
                      "issue": "移动目的地的助词错误。",
                      "suggestion": "私は昨日、図書館へ行きました。",
                      "reviewSourceText": "我昨天去了图书馆。",
                      "severity": "MEDIUM",
                      "suggestedUserErrorTypeName": "移动目的地误用助词を",
                      "suggestedUserErrorTypeDescription": "表示移动目的地时应使用に或へ。"
                    }],
                    "revisionSuggestions": [],
                    "recommendedExpressions": []
                  }
                }
                """;

        var review = validator.validate(
                response,
                "私は昨日、図書館を行きました。",
                Map.of("PARTICLE", option("PARTICLE", "助词错误"))
        );

        assertThat(review.errorAnalysis()).hasSize(1);
        assertThat(review.errorAnalysis().getFirst().reviewSourceText()).isEqualTo("我昨天去了图书馆。");
        assertThat(review.correctedText()).isEqualTo("私は昨日、図書館へ行きました。");
    }

    @Test
    void shouldDropTranslationOnlyOrUnrelatedErrorsWithoutArticleValidation() {
        String response = """
                {
                  "review": {
                    "scores": {
                      "grammarVocabularyScore": 90,
                      "naturalFluencyScore": 90,
                      "scenarioAdaptationScore": 90,
                      "informationCompletenessScore": 90
                    },
                    "totalScore": 90,
                    "correctedText": "今日は晴れです。",
                    "overallComment": "文本自然。",
                    "comments": {
                      "grammarVocabularyComment": "正确。",
                      "naturalFluencyComment": "自然。",
                      "styleConsistencyComment": "一致。",
                      "writingCompletenessComment": "完整。"
                    },
                    "errorAnalysis": [{
                      "errorTypeCode": "OMISSION",
                      "original": "今日は",
                      "issue": "不应按翻译漏译判断。",
                      "suggestion": "今日は晴れです。",
                      "reviewSourceText": "今天是晴天。",
                      "severity": "LOW",
                      "suggestedUserErrorTypeName": "不适用错误",
                      "suggestedUserErrorTypeDescription": "纯日语纠错不使用漏译分类。"
                    }],
                    "revisionSuggestions": [],
                    "recommendedExpressions": []
                  }
                }
                """;

        var review = validator.validate(response, "今日は晴れです。", Map.of(
                "PARTICLE", option("PARTICLE", "助词错误")
        ));

        assertThat(review.errorAnalysis()).isEmpty();
    }

    private AiErrorTypeOptionDTO option(String code, String name) {
        return new AiErrorTypeOptionDTO(1L, code, name, "说明", "GRAMMAR_SYNTAX", "语法与句法");
    }
}
