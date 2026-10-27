package com.sazare.util;

import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import java.util.regex.Pattern;

public final class ChatReviewTextValidator {
    private static final Pattern KANA = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}]");
    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    private ChatReviewTextValidator() { }

    public static void validate(String source, String answer, String learningMode) {
        boolean chinese = "ZH_TO_JA".equals(learningMode);
        if (chinese ? !HAN.matcher(source).find() : HAN.matcher(source).find() || !LATIN.matcher(source).find()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "复习题面必须使用当前学习模式的中文或英文");
        }
        if (!KANA.matcher(answer).find()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "日语参考答案必须包含日语假名");
        }
    }
}
