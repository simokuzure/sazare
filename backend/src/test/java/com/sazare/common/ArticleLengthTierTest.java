package com.sazare.common;

import com.sazare.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleLengthTierTest {

    @Test
    void shouldDefaultToMediumAndRejectUnsupportedValue() {
        assertThat(ArticleLengthTier.from(null)).isEqualTo(ArticleLengthTier.MEDIUM);
        assertThat(ArticleLengthTier.from(" ")).isEqualTo(ArticleLengthTier.MEDIUM);
        assertThatThrownBy(() -> ArticleLengthTier.from("EXTRA_LONG"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("lengthTier 只能是 SHORT、MEDIUM 或 LONG");
    }

    @Test
    void shouldExposeChineseRanges() {
        assertRange(ArticleLengthTier.SHORT, TranslationDirection.ZH_TO_JA, 60, 100);
        assertRange(ArticleLengthTier.MEDIUM, TranslationDirection.ZH_TO_JA, 120, 180);
        assertRange(ArticleLengthTier.LONG, TranslationDirection.ZH_TO_JA, 200, 280);
    }

    @Test
    void shouldExposeEnglishRanges() {
        assertRange(ArticleLengthTier.SHORT, TranslationDirection.EN_TO_JA, 45, 75);
        assertRange(ArticleLengthTier.MEDIUM, TranslationDirection.EN_TO_JA, 90, 135);
        assertRange(ArticleLengthTier.LONG, TranslationDirection.EN_TO_JA, 150, 210);
    }

    private void assertRange(
            ArticleLengthTier tier,
            TranslationDirection direction,
            int expectedMinimum,
            int expectedMaximum
    ) {
        assertThat(tier.minimum(direction)).isEqualTo(expectedMinimum);
        assertThat(tier.maximum(direction)).isEqualTo(expectedMaximum);
    }
}
