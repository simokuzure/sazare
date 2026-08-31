package com.sazare.common;

import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;

import java.util.Locale;

/**
 * 中译日、英译日的统一方向定义。题型、界面语言和 Prompt 规则必须从这里派生。
 */
public enum TranslationDirection {

    ZH_TO_JA(
            "ZH_TO_JA", "zh-CN", "中文", "Chinese", "中文",
            "TRANSLATION_ZH_TO_JA", "TRANSLATION_ZH_TO_JA_ARTICLE",
            "个非空白字符"
    ),
    EN_TO_JA(
            "EN_TO_JA", "en-US", "英文", "English", "English",
            "TRANSLATION_EN_TO_JA", "TRANSLATION_EN_TO_JA_ARTICLE",
            "words"
    );

    public static final String LEARNING_MODE_PATTERN = "ZH_TO_JA|EN_TO_JA";
    public static final String QUESTION_TYPE_PATTERN = "TRANSLATION_(ZH|EN)_TO_JA(_ARTICLE)?";
    public static final String SHORT_QUESTION_TYPE_PATTERN = "TRANSLATION_(ZH|EN)_TO_JA";

    private static final String ENGLISH_PROMPT_RULES = """
            [Translation direction rules]
            learningMode: EN_TO_JA
            task: English-to-Japanese
            sourceLanguage: English
            feedbackLanguage: English
            shortQuestionType: TRANSLATION_EN_TO_JA
            articleQuestionType: TRANSLATION_EN_TO_JA_ARTICLE

            These direction rules take precedence over the reusable prompt body below.
            Chinese wording in the reusable body describes shared field semantics or examples;
            it must not change the source language, feedback language, or questionType above.
            The source language and all explanatory output must be English.
            In scoring, correction, and review JSON, overallComment, every comments field,
            sentenceReviews[].comment, errorAnalysis[].issue,
            errorAnalysis[].suggestedUserErrorTypeName,
            errorAnalysis[].suggestedUserErrorTypeDescription, revisionSuggestions[],
            recommendedExpressions[].usage, recommendedExpressions[].note, feedback,
            and reviewSourceText must be English. Do not output Chinese in these fields,
            even if a Chinese placeholder or instruction appears later in the prompt.
            Japanese answers, correctedText, revisedText, referenceText,
            errorAnalysis[].suggestion, and recommendedExpressions[].expression must remain Japanese.
            """;

    private final String learningMode;
    private final String uiLanguage;
    private final String sourceLanguageNameZh;
    private final String sourceLanguageNameEn;
    private final String feedbackLanguage;
    private final String shortQuestionType;
    private final String articleQuestionType;
    private final String articleLengthUnit;

    TranslationDirection(
            String learningMode,
            String uiLanguage,
            String sourceLanguageNameZh,
            String sourceLanguageNameEn,
            String feedbackLanguage,
            String shortQuestionType,
            String articleQuestionType,
            String articleLengthUnit
    ) {
        this.learningMode = learningMode;
        this.uiLanguage = uiLanguage;
        this.sourceLanguageNameZh = sourceLanguageNameZh;
        this.sourceLanguageNameEn = sourceLanguageNameEn;
        this.feedbackLanguage = feedbackLanguage;
        this.shortQuestionType = shortQuestionType;
        this.articleQuestionType = articleQuestionType;
        this.articleLengthUnit = articleLengthUnit;
    }

    public static TranslationDirection fromLearningMode(String learningMode) {
        if (learningMode == null || learningMode.isBlank()) {
            return ZH_TO_JA;
        }
        try {
            return valueOf(learningMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "learningMode 只能是 ZH_TO_JA 或 EN_TO_JA");
        }
    }

    public static TranslationDirection fromQuestionType(String questionType) {
        for (TranslationDirection direction : values()) {
            if (direction.shortQuestionType.equals(questionType)
                    || direction.articleQuestionType.equals(questionType)) {
                return direction;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "questionType 不合法");
    }

    public boolean isArticle(String questionType) {
        return articleQuestionType.equals(questionType);
    }

    public int countArticleLength(String text) {
        if (this == EN_TO_JA) {
            String normalized = text == null ? "" : text.trim();
            return normalized.isEmpty() ? 0 : normalized.split("\\s+").length;
        }
        return text == null ? 0 : text.replaceAll("\\s", "").length();
    }

    public String applyPromptRules(String prompt) {
        if (this == ZH_TO_JA) {
            return prompt;
        }
        return ENGLISH_PROMPT_RULES + "\n" + prompt;
    }

    public String displayText(String chinese, String english) {
        if (this == EN_TO_JA && english != null && !english.isBlank()) {
            return english;
        }
        return chinese;
    }

    public String learningMode() { return learningMode; }
    public String uiLanguage() { return uiLanguage; }
    public String sourceLanguageNameZh() { return sourceLanguageNameZh; }
    public String sourceLanguageNameEn() { return sourceLanguageNameEn; }
    public String feedbackLanguage() { return feedbackLanguage; }
    public String shortQuestionType() { return shortQuestionType; }
    public String articleQuestionType() { return articleQuestionType; }
    public String articleLengthUnit() { return articleLengthUnit; }
}
