package com.sazare.common;

import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;

/**
 * 文章生成长度档位。中文按非空白字符计数，英文按空白分隔的单词计数。
 */
public enum ArticleLengthTier {

    SHORT(60, 100, 45, 75),
    MEDIUM(120, 180, 90, 135),
    LONG(200, 280, 150, 210);

    public static final String PATTERN = "SHORT|MEDIUM|LONG";

    private final int chineseMinimum;
    private final int chineseMaximum;
    private final int englishMinimum;
    private final int englishMaximum;

    ArticleLengthTier(int chineseMinimum, int chineseMaximum, int englishMinimum, int englishMaximum) {
        this.chineseMinimum = chineseMinimum;
        this.chineseMaximum = chineseMaximum;
        this.englishMinimum = englishMinimum;
        this.englishMaximum = englishMaximum;
    }

    public static ArticleLengthTier from(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "lengthTier 只能是 SHORT、MEDIUM 或 LONG");
        }
    }

    public int minimum(TranslationDirection direction) {
        return direction == TranslationDirection.EN_TO_JA ? englishMinimum : chineseMinimum;
    }

    public int maximum(TranslationDirection direction) {
        return direction == TranslationDirection.EN_TO_JA ? englishMaximum : chineseMaximum;
    }
}
