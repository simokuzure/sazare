package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerErrorAnalysisDTO;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AiErrorAnalysisValidator {

    private static final Set<String> VALID_SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");

    public void validate(
            List<AiAnswerErrorAnalysisDTO> errorAnalysis,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode,
            String answerText
    ) {
        if (errorAnalysis == null) {
            throw invalid("errorAnalysis 不能为空");
        }
        Set<String> errorKeys = new LinkedHashSet<>();
        for (AiAnswerErrorAnalysisDTO error : errorAnalysis) {
            if (error == null) {
                throw invalid("errorAnalysis 项不能为空");
            }
            if (!errorTypesByCode.containsKey(error.errorTypeCode())) {
                throw invalid("errorAnalysis.errorTypeCode 不合法");
            }
            requireText(error.original(), "errorAnalysis.original 不能为空");
            String original = error.original().trim();
            if (!answerText.contains(original)) {
                throw invalid("errorAnalysis.original 不属于用户答案");
            }
            requireText(error.issue(), "errorAnalysis.issue 不能为空");
            requireText(error.suggestion(), "errorAnalysis.suggestion 不能为空");
            if (!VALID_SEVERITIES.contains(error.severity())) {
                throw invalid("errorAnalysis.severity 不合法");
            }
            requireText(error.suggestedUserErrorTypeName(), "errorAnalysis.suggestedUserErrorTypeName 不能为空");
            requireText(error.suggestedUserErrorTypeDescription(), "errorAnalysis.suggestedUserErrorTypeDescription 不能为空");
            if (error.suggestedUserErrorTypeName().trim().length() > 128) {
                throw invalid("建议的用户错误类型名称过长");
            }
            if (error.suggestedUserErrorTypeDescription().trim().length() > 255) {
                throw invalid("建议的用户错误类型说明过长");
            }
            if (!errorKeys.add(error.errorTypeCode() + "\u0000" + original)) {
                throw invalid("errorAnalysis 存在重复错误");
            }
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 " + message);
    }
}
