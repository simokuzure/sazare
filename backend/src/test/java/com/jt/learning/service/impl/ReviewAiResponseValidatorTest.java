package com.jt.learning.service.impl;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewAiResponseValidatorTest {

    private ReviewAiResponseValidator validator;
    private Map<String, AiErrorTypeOptionDTO> options;

    @BeforeEach
    void setUp() {
        validator = new ReviewAiResponseValidator(new ObjectMapper(), new AiErrorAnalysisValidator());
        options = Map.of("PARTICLE_CASE", new AiErrorTypeOptionDTO(
                1L, "PARTICLE_CASE", "格助词", "格助词误用", "PARTICLE", "助词"));
    }

    @Test
    void shouldParseValidScoringResponse() {
        var review = validator.parseScoring("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":80,"naturalFluencyScore":81,"scenarioAdaptationScore":82,"informationCompletenessScore":83},"errorAnalysis":[]}}
                """, options, "電車に間に合いました");

        assertThat(review.quality()).isEqualTo(4);
        assertThat(review.targetErrorResolved()).isTrue();
    }

    @Test
    void shouldAcceptScoringFieldsInAnyOrder() {
        var review = validator.parseScoring("""
                {"review":{"feedback":"目标错误已解决。","errorAnalysis":[],"scores":{"informationCompletenessScore":83,"scenarioAdaptationScore":82,"naturalFluencyScore":81,"grammarVocabularyScore":80},"targetErrorResolved":true,"quality":4}}
                """, options, "電車に間に合いました");

        assertThat(review.quality()).isEqualTo(4);
        assertThat(review.feedback()).isEqualTo("目标错误已解决。");
    }

    @Test
    void shouldRejectInconsistentQualityAndResolution() {
        assertThatThrownBy(() -> validator.parseScoring("""
                {"review":{"quality":2,"targetErrorResolved":true,"feedback":"仍有错误。","scores":{"grammarVocabularyScore":60,"naturalFluencyScore":60,"scenarioAdaptationScore":60,"informationCompletenessScore":60},"errorAnalysis":[]}}
                """, options, "電車を間に合いました"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void shouldRejectCandidateWhoseOriginalIsNotInAnswer() {
        assertThatThrownBy(() -> validator.parseScoring("""
                {"review":{"quality":3,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":70,"naturalFluencyScore":70,"scenarioAdaptationScore":70,"informationCompletenessScore":70},"errorAnalysis":[{"errorTypeCode":"PARTICLE_CASE","original":"バスを","issue":"助词错误","suggestion":"使用に","severity":"MEDIUM","suggestedUserErrorTypeName":"赶上交通工具时误用を","suggestedUserErrorTypeDescription":"赶上交通工具时应使用に。"}]}}
                """, options, "電車に間に合いました"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于用户答案");
    }

    @Test
    void shouldRejectMissingOrOutOfRangeScores() {
        assertThatThrownBy(() -> validator.parseScoring("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","errorAnalysis":[]}}
                """, options, "電車に間に合いました"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字段不合法");

        assertThatThrownBy(() -> validator.parseScoring("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":101,"naturalFluencyScore":81,"scenarioAdaptationScore":82,"informationCompletenessScore":83},"errorAnalysis":[]}}
                """, options, "電車に間に合いました"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grammarVocabularyScore");

        assertThatThrownBy(() -> validator.parseScoring("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":80,"naturalFluencyScore":81,"scenarioAdaptationScore":82,"informationCompletenessScore":83,"extraScore":90},"errorAnalysis":[]}}
                """, options, "電車に間に合いました"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字段不合法");
    }

    @Test
    void shouldAcceptScoreBoundaries() {
        var review = validator.parseScoring("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":0,"naturalFluencyScore":100,"scenarioAdaptationScore":0,"informationCompletenessScore":100},"errorAnalysis":[]}}
                """, options, "電車に間に合いました");

        assertThat(review.scores().grammarVocabularyScore()).isZero();
        assertThat(review.scores().naturalFluencyScore()).isEqualTo(100);
    }

    @Test
    void shouldRejectDuplicateQuestionOrInvalidAnswers() {
        String duplicate = """
                {"question":{"sourceText":"请翻译这句话","contextText":"日常交流","grammarPoint":"助词","answers":[{"answerText":"この文を訳してください。","answerType":"STANDARD","primaryAnswer":true,"sortOrder":0}]}}
                """;
        assertThatThrownBy(() -> validator.parseQuestion(duplicate, Set.of("请翻译这句话")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重复");

        String invalidAnswers = """
                {"question":{"sourceText":"请翻译另一句话","contextText":"日常交流","grammarPoint":"助词","answers":[{"answerText":"答えです。","answerType":"OTHER","primaryAnswer":true,"sortOrder":0}]}}
                """;
        assertThatThrownBy(() -> validator.parseQuestion(invalidAnswers, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("answerType");
    }

    @Test
    void shouldAcceptGeneratedQuestionFieldsInAnyOrder() {
        var question = validator.parseQuestion("""
                {"question":{"answers":[{"sortOrder":0,"primaryAnswer":true,"answerType":"STANDARD","answerText":"この文を訳してください。"}],"grammarPoint":"助词","sourceText":"请翻译这句话","contextText":"日常交流"}}
                """, Set.of());

        assertThat(question.sourceText()).isEqualTo("请翻译这句话");
        assertThat(question.answers()).hasSize(1);
    }
}
