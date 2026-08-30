package com.sazare.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationDirectionTest {

    @Test
    void shouldDefaultToChineseAndMapAllQuestionTypes() {
        assertThat(TranslationDirection.fromLearningMode(null)).isEqualTo(TranslationDirection.ZH_TO_JA);
        assertThat(TranslationDirection.fromQuestionType("TRANSLATION_ZH_TO_JA")).isEqualTo(TranslationDirection.ZH_TO_JA);
        assertThat(TranslationDirection.fromQuestionType("TRANSLATION_ZH_TO_JA_ARTICLE")).isEqualTo(TranslationDirection.ZH_TO_JA);
        assertThat(TranslationDirection.fromQuestionType("TRANSLATION_EN_TO_JA")).isEqualTo(TranslationDirection.EN_TO_JA);
        assertThat(TranslationDirection.fromQuestionType("TRANSLATION_EN_TO_JA_ARTICLE")).isEqualTo(TranslationDirection.EN_TO_JA);
    }

    @Test
    void shouldKeepDirectionRulesTogether() {
        TranslationDirection direction = TranslationDirection.EN_TO_JA;

        assertThat(direction.uiLanguage()).isEqualTo("en-US");
        assertThat(direction.shortQuestionType()).isEqualTo("TRANSLATION_EN_TO_JA");
        assertThat(direction.articleQuestionType()).isEqualTo("TRANSLATION_EN_TO_JA_ARTICLE");
        assertThat(direction.articleLengthUnit()).isEqualTo("words");
        assertThat(direction.countArticleLength("one two\nthree")).isEqualTo(3);
        assertThat(direction.countArticleLength("word ".repeat(210))).isEqualTo(210);
    }

    @Test
    void shouldAdaptPromptWithoutDuplicatingService() {
        String prompt = "请生成中译日题目，题型 TRANSLATION_ZH_TO_JA，用中文说明。";

        assertThat(TranslationDirection.EN_TO_JA.adaptPrompt(prompt))
                .contains("English-to-Japanese")
                .contains("TRANSLATION_EN_TO_JA")
                .contains("revisionSuggestions[]")
                .contains("must remain Japanese")
                .contains("English");
        assertThat(TranslationDirection.ZH_TO_JA.adaptPrompt(prompt)).isEqualTo(prompt);
    }
}
