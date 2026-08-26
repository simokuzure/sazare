package com.jt.learning.service.ai.validation;

import com.jt.learning.dto.AiAnswerErrorAnalysisDTO;
import com.jt.learning.dto.AiAnswerRecommendedExpressionDTO;
import com.jt.learning.dto.AiAnswerScoresDTO;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiJapaneseCorrectionCommentsDTO;
import com.jt.learning.dto.AiJapaneseCorrectionErrorDTO;
import com.jt.learning.dto.AiJapaneseCorrectionResponseDTO;
import com.jt.learning.dto.AiJapaneseCorrectionReviewDTO;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class JapaneseCorrectionAiResponseValidator {

    private static final int MAX_TEXT_LENGTH = 5000;
    private static final String PUNCTUATION_ERROR_CODE = "PUNCTUATION";
    private static final Set<String> VALID_FORMALITIES = Set.of("CASUAL", "NEUTRAL", "POLITE", "BUSINESS");
    private static final Pattern JAPANESE_KANA_PATTERN = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}]");
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    private final ObjectMapper objectMapper;
    private final AiErrorAnalysisValidator errorAnalysisValidator;

    public JapaneseCorrectionAiResponseValidator(
            ObjectMapper objectMapper,
            AiErrorAnalysisValidator errorAnalysisValidator
    ) {
        this.objectMapper = objectMapper;
        this.errorAnalysisValidator = errorAnalysisValidator;
    }

    public AiJapaneseCorrectionReviewDTO validate(
            String aiContent,
            String originalText,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        AiJapaneseCorrectionReviewDTO review = parse(aiContent);
        if (review == null) {
            throw invalid("review 不能为空");
        }

        validateScores(review.scores());
        String correctedText = requireText(review.correctedText(), "correctedText 不能为空");
        if (correctedText.length() > MAX_TEXT_LENGTH) {
            throw invalid("correctedText 长度不能超过 5000");
        }
        String overallComment = requireText(review.overallComment(), "overallComment 不能为空");
        AiJapaneseCorrectionCommentsDTO comments = normalizeComments(review.comments());
        List<AiJapaneseCorrectionErrorDTO> errors = sanitizeErrors(
                review.errorAnalysis(), originalText, correctedText, errorTypesByCode);
        List<String> suggestions = normalizeSuggestions(review.revisionSuggestions());
        List<AiAnswerRecommendedExpressionDTO> expressions = normalizeExpressions(review.recommendedExpressions());

        return new AiJapaneseCorrectionReviewDTO(
                review.scores(),
                review.totalScore(),
                correctedText,
                overallComment,
                comments,
                errors,
                suggestions,
                expressions
        );
    }

    private AiJapaneseCorrectionReviewDTO parse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw invalid("输出为空");
        }
        try {
            JsonNode root = objectMapper.readTree(aiContent);
            if (!root.isObject() || !Set.copyOf(root.propertyNames()).equals(Set.of("review"))) {
                throw invalid("JSON 顶层只能包含 review 字段");
            }
            if (root.get("review") == null || !root.get("review").isObject()) {
                throw invalid("review 必须是对象");
            }
            return objectMapper.treeToValue(root, AiJapaneseCorrectionResponseDTO.class).review();
        } catch (JacksonException exception) {
            throw invalid("输出不是合法 JSON");
        }
    }

    private void validateScores(AiAnswerScoresDTO scores) {
        if (scores == null) {
            throw invalid("scores 不能为空");
        }
        validateScore(scores.grammarVocabularyScore(), "grammarVocabularyScore");
        validateScore(scores.naturalFluencyScore(), "naturalFluencyScore");
        validateScore(scores.scenarioAdaptationScore(), "scenarioAdaptationScore");
        validateScore(scores.informationCompletenessScore(), "informationCompletenessScore");
    }

    private void validateScore(Integer score, String fieldName) {
        if (score == null || score < 0 || score > 100) {
            throw invalid(fieldName + " 必须是 0 到 100 的整数");
        }
    }

    private AiJapaneseCorrectionCommentsDTO normalizeComments(AiJapaneseCorrectionCommentsDTO comments) {
        if (comments == null) {
            throw invalid("comments 不能为空");
        }
        return new AiJapaneseCorrectionCommentsDTO(
                requireText(comments.grammarVocabularyComment(), "grammarVocabularyComment 不能为空"),
                requireText(comments.naturalFluencyComment(), "naturalFluencyComment 不能为空"),
                requireText(comments.styleConsistencyComment(), "styleConsistencyComment 不能为空"),
                requireText(comments.writingCompletenessComment(), "writingCompletenessComment 不能为空")
        );
    }

    private List<AiJapaneseCorrectionErrorDTO> sanitizeErrors(
            List<AiJapaneseCorrectionErrorDTO> errors,
            String originalText,
            String correctedText,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        if (errors == null) {
            return List.of();
        }

        Map<String, AiJapaneseCorrectionErrorDTO> candidatesByKey = new LinkedHashMap<>();
        for (AiJapaneseCorrectionErrorDTO error : errors) {
            if (error == null || PUNCTUATION_ERROR_CODE.equals(error.errorTypeCode())
                    || !hasCorrectionFields(error, correctedText)) {
                continue;
            }
            String key = error.errorTypeCode() + "\u0000" + error.original().trim();
            candidatesByKey.putIfAbsent(key, error);
        }

        List<AiAnswerErrorAnalysisDTO> genericErrors = candidatesByKey.values().stream()
                .map(AiJapaneseCorrectionErrorDTO::toAnswerError)
                .toList();
        List<AiAnswerErrorAnalysisDTO> sanitized = errorAnalysisValidator.sanitize(
                genericErrors, errorTypesByCode, originalText);
        errorAnalysisValidator.validate(sanitized, errorTypesByCode, originalText);

        List<AiJapaneseCorrectionErrorDTO> normalized = new ArrayList<>();
        for (AiAnswerErrorAnalysisDTO genericError : sanitized) {
            String key = genericError.errorTypeCode() + "\u0000" + genericError.original().trim();
            AiJapaneseCorrectionErrorDTO error = candidatesByKey.get(key);
            normalized.add(new AiJapaneseCorrectionErrorDTO(
                    genericError.errorTypeCode(),
                    genericError.original().trim(),
                    genericError.issue().trim(),
                    genericError.suggestion().trim(),
                    error.reviewSourceText().trim(),
                    genericError.severity(),
                    genericError.suggestedUserErrorTypeName().trim(),
                    genericError.suggestedUserErrorTypeDescription().trim()
            ));
        }
        return List.copyOf(normalized);
    }

    private boolean hasCorrectionFields(AiJapaneseCorrectionErrorDTO error, String correctedText) {
        if (error == null || error.suggestion() == null || error.suggestion().isBlank()
                || error.reviewSourceText() == null || error.reviewSourceText().isBlank()) {
            return false;
        }
        String suggestion = error.suggestion().trim();
        String reviewSourceText = error.reviewSourceText().trim();
        return correctedText.contains(suggestion)
                && reviewSourceText.length() <= 1000
                && !JAPANESE_KANA_PATTERN.matcher(reviewSourceText).find();
    }

    private List<String> normalizeSuggestions(List<String> suggestions) {
        if (suggestions == null) {
            return List.of();
        }
        return suggestions.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .limit(20)
                .toList();
    }

    private List<AiAnswerRecommendedExpressionDTO> normalizeExpressions(
            List<AiAnswerRecommendedExpressionDTO> expressions
    ) {
        if (expressions == null) {
            return List.of();
        }
        return expressions.stream()
                .filter(expression -> expression != null
                        && expression.expression() != null && !expression.expression().isBlank()
                        && expression.usage() != null && !expression.usage().isBlank()
                        && VALID_FORMALITIES.contains(expression.formality())
                        && expression.note() != null && !expression.note().isBlank())
                .map(expression -> new AiAnswerRecommendedExpressionDTO(
                        expression.expression().trim(),
                        expression.usage().trim(),
                        expression.formality(),
                        expression.note().trim()))
                .limit(20)
                .toList();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 日语纠错 " + message);
    }
}
