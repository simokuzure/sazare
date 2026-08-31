package com.sazare.service.ai.validation;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiAnswerErrorAnalysisDTO;
import com.sazare.dto.AiAnswerRecommendedExpressionDTO;
import com.sazare.dto.AiAnswerReviewCommentsDTO;
import com.sazare.dto.AiAnswerReviewDTO;
import com.sazare.dto.AiAnswerScoresDTO;
import com.sazare.dto.AiAnswerScoringResponseDTO;
import com.sazare.dto.AiArticleSentenceReviewDTO;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiAnswerScoringResponseValidator {

    private static final Set<String> VALID_FORMALITIES = Set.of("CASUAL", "NEUTRAL", "POLITE", "BUSINESS");
    private static final int ARTICLE_MAX_SENTENCES = 30;

    private final ObjectMapper objectMapper;
    private final AiErrorAnalysisValidator errorAnalysisValidator;

    public AiAnswerScoringResponseValidator(
            ObjectMapper objectMapper,
            AiErrorAnalysisValidator errorAnalysisValidator
    ) {
        this.objectMapper = objectMapper;
        this.errorAnalysisValidator = errorAnalysisValidator;
    }

    public AiAnswerReviewDTO validate(
            String aiContent,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode,
            String answerText,
            Question question,
            List<QuestionAnswer> standardAnswers
    ) {
        AiAnswerScoringResponseDTO response = parse(aiContent);
        if (response.review() == null) {
            throw invalid("AI 评分 review 不能为空");
        }

        AiAnswerReviewDTO review = response.review();
        validateScores(review.scores());
        requireText(review.overallComment(), "AI 评分 overallComment 不能为空");
        validateComments(review.comments());
        review = normalizeOptionalContent(review);
        if (TranslationDirection.fromQuestionType(question.getQuestionType()).isArticle(question.getQuestionType())) {
            List<String> sourceSegments = splitArticleSegments(question.getSourceText(), "sourceText");
            List<String> referenceSegments = splitArticleSegments(
                    standardAnswers.getFirst().getAnswerText(), "answerText");
            review = normalizeArticleSentenceReviews(
                    review, answerText, sourceSegments, referenceSegments);
            review = copyWithErrors(review, errorAnalysisValidator.sanitizeArticle(
                    review.errorAnalysis(), errorTypesByCode, answerText, sourceSegments, referenceSegments));
            validateArticleSentenceReviews(review.sentenceReviews(), sourceSegments, referenceSegments, answerText);
            errorAnalysisValidator.validateArticle(
                    review.errorAnalysis(), errorTypesByCode, answerText, sourceSegments, referenceSegments);
        } else {
            review = copyWithErrors(review, errorAnalysisValidator.sanitize(
                    review.errorAnalysis(), errorTypesByCode, answerText));
            errorAnalysisValidator.validate(review.errorAnalysis(), errorTypesByCode, answerText);
        }
        validateRevisionSuggestions(review.revisionSuggestions());
        validateRecommendedExpressions(review.recommendedExpressions());
        return review;
    }

    private AiAnswerScoringResponseDTO parse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw invalid("AI 评分输出为空");
        }
        try {
            JsonNode root = objectMapper.readTree(aiContent);
            if (!root.isObject()) {
                throw invalid("AI 评分 JSON 顶层必须是对象");
            }
            List<String> fields = new ArrayList<>(root.propertyNames());
            if (!fields.equals(List.of("review"))) {
                throw invalid("AI 评分 JSON 顶层只能包含 review 字段");
            }
            if (!root.get("review").isObject()) {
                throw invalid("AI 评分 review 必须是对象");
            }
            return objectMapper.treeToValue(root, AiAnswerScoringResponseDTO.class);
        } catch (JacksonException exception) {
            throw invalid("AI 评分输出不是合法 JSON");
        }
    }

    private AiAnswerReviewDTO normalizeOptionalContent(AiAnswerReviewDTO review) {
        List<String> revisionSuggestions = review.revisionSuggestions() == null
                ? List.of()
                : review.revisionSuggestions().stream()
                        .filter(suggestion -> suggestion != null && !suggestion.isBlank())
                        .map(String::trim)
                        .toList();
        List<AiAnswerRecommendedExpressionDTO> recommendedExpressions = review.recommendedExpressions() == null
                ? List.of()
                : review.recommendedExpressions().stream()
                        .filter(this::isCompleteRecommendedExpression)
                        .toList();
        return new AiAnswerReviewDTO(
                review.scores(), calculateTotalScore(review.scores()), review.overallComment(), review.comments(),
                review.sentenceReviews(), review.errorAnalysis(), revisionSuggestions, recommendedExpressions);
    }

    private boolean isCompleteRecommendedExpression(AiAnswerRecommendedExpressionDTO expression) {
        return expression != null
                && expression.expression() != null && !expression.expression().isBlank()
                && expression.usage() != null && !expression.usage().isBlank()
                && VALID_FORMALITIES.contains(expression.formality())
                && expression.note() != null && !expression.note().isBlank();
    }

    private AiAnswerReviewDTO normalizeArticleSentenceReviews(
            AiAnswerReviewDTO review,
            String answerText,
            List<String> sourceSegments,
            List<String> referenceSegments
    ) {
        List<AiArticleSentenceReviewDTO> sentenceReviews = review.sentenceReviews();
        if (sentenceReviews == null || sentenceReviews.size() != sourceSegments.size()) {
            return review;
        }
        Map<Integer, AiArticleSentenceReviewDTO> reviewsByIndex = new LinkedHashMap<>();
        for (AiArticleSentenceReviewDTO sentenceReview : sentenceReviews) {
            if (sentenceReview == null || sentenceReview.sourceSegmentIndex() == null
                    || sentenceReview.sourceSegmentIndex() < 0
                    || sentenceReview.sourceSegmentIndex() >= sourceSegments.size()
                    || reviewsByIndex.putIfAbsent(sentenceReview.sourceSegmentIndex(), sentenceReview) != null) {
                return review;
            }
        }
        List<AiArticleSentenceReviewDTO> normalized = new ArrayList<>();
        for (int index = 0; index < sourceSegments.size(); index++) {
            AiArticleSentenceReviewDTO sentenceReview = reviewsByIndex.get(index);
            if (sentenceReview == null) {
                return review;
            }
            normalized.add(normalizeArticleSentenceReview(
                    sentenceReview, index, answerText, sourceSegments.get(index), referenceSegments.get(index),
                    review.overallComment()));
        }
        return new AiAnswerReviewDTO(
                review.scores(), review.totalScore(), review.overallComment(), review.comments(), List.copyOf(normalized),
                review.errorAnalysis(), review.revisionSuggestions(), review.recommendedExpressions());
    }

    private AiArticleSentenceReviewDTO normalizeArticleSentenceReview(
            AiArticleSentenceReviewDTO sentenceReview,
            int index,
            String answerText,
            String sourceText,
            String referenceText,
            String overallComment
    ) {
        String answerExcerpt = sentenceReview.answerExcerpt();
        if (answerExcerpt != null) {
            answerExcerpt = normalizeText(answerExcerpt);
            if (answerExcerpt.isBlank()) {
                answerExcerpt = null;
            } else if (!answerText.contains(answerExcerpt)) {
                answerExcerpt = answerText;
            }
        }
        return new AiArticleSentenceReviewDTO(
                index, sourceText, referenceText, answerExcerpt, referenceText,
                sentenceReview.comment() == null || sentenceReview.comment().isBlank()
                        ? overallComment
                        : sentenceReview.comment().trim());
    }

    private AiAnswerReviewDTO copyWithErrors(
            AiAnswerReviewDTO review,
            List<AiAnswerErrorAnalysisDTO> errors
    ) {
        return new AiAnswerReviewDTO(
                review.scores(), review.totalScore(), review.overallComment(), review.comments(),
                review.sentenceReviews(), errors, review.revisionSuggestions(), review.recommendedExpressions());
    }

    private void validateArticleSentenceReviews(
            List<AiArticleSentenceReviewDTO> sentenceReviews,
            List<String> sourceSegments,
            List<String> referenceSegments,
            String answerText
    ) {
        if (sourceSegments.size() != referenceSegments.size()) {
            throw invalid("文章题中文与日文参考段落数不一致");
        }
        if (sentenceReviews == null || sentenceReviews.size() != sourceSegments.size()) {
            throw invalid("AI 评分 sentenceReviews 数量不一致");
        }
        for (int index = 0; index < sentenceReviews.size(); index++) {
            AiArticleSentenceReviewDTO review = sentenceReviews.get(index);
            if (review == null || review.sourceSegmentIndex() == null || review.sourceSegmentIndex() != index) {
                throw invalid("AI 评分 sentenceReviews 索引不连续");
            }
            if (!sourceSegments.get(index).equals(review.sourceText())
                    || !referenceSegments.get(index).equals(review.referenceText())) {
                throw invalid("AI 评分逐句原文或参考答案不一致");
            }
            if (review.answerExcerpt() != null && !review.answerExcerpt().isBlank()
                    && !answerText.contains(review.answerExcerpt())) {
                throw invalid("AI 评分 answerExcerpt 不属于用户完整答案");
            }
            if (!referenceSegments.get(index).equals(review.revisedText())) {
                throw invalid("AI 评分 revisedText 必须是对应完整参考句");
            }
            requireText(review.comment(), "AI 评分 sentenceReviews.comment 不能为空");
        }
    }

    private void validateScores(AiAnswerScoresDTO scores) {
        if (scores == null) {
            throw invalid("AI 评分 scores 不能为空");
        }
        validateScore(scores.grammarVocabularyScore(), "grammarVocabularyScore");
        validateScore(scores.naturalFluencyScore(), "naturalFluencyScore");
        validateScore(scores.scenarioAdaptationScore(), "scenarioAdaptationScore");
        validateScore(scores.informationCompletenessScore(), "informationCompletenessScore");
    }

    private void validateScore(Integer score, String fieldName) {
        if (score == null || score < 0 || score > 100) {
            throw invalid("AI 评分 " + fieldName + " 不合法");
        }
    }

    private void validateComments(AiAnswerReviewCommentsDTO comments) {
        if (comments == null) {
            throw invalid("AI 评分 comments 不能为空");
        }
        requireText(comments.grammarComment(), "AI 评分 grammarComment 不能为空");
        requireText(comments.vocabularyComment(), "AI 评分 vocabularyComment 不能为空");
        requireText(comments.naturalnessComment(), "AI 评分 naturalnessComment 不能为空");
        requireText(comments.scenarioComment(), "AI 评分 scenarioComment 不能为空");
    }

    private void validateRevisionSuggestions(List<String> revisionSuggestions) {
        if (revisionSuggestions == null) {
            throw invalid("AI 评分 revisionSuggestions 不能为空");
        }
        revisionSuggestions.forEach(suggestion ->
                requireText(suggestion, "AI 评分 revisionSuggestions 项不能为空"));
    }

    private void validateRecommendedExpressions(List<AiAnswerRecommendedExpressionDTO> expressions) {
        if (expressions == null) {
            throw invalid("AI 评分 recommendedExpressions 不能为空");
        }
        for (AiAnswerRecommendedExpressionDTO expression : expressions) {
            if (expression == null) {
                throw invalid("AI 评分 recommendedExpressions 项不能为空");
            }
            requireText(expression.expression(), "AI 评分 recommendedExpressions.expression 不能为空");
            requireText(expression.usage(), "AI 评分 recommendedExpressions.usage 不能为空");
            if (!VALID_FORMALITIES.contains(expression.formality())) {
                throw invalid("AI 评分 recommendedExpressions.formality 不合法");
            }
            requireText(expression.note(), "AI 评分 recommendedExpressions.note 不能为空");
        }
    }

    private BigDecimal calculateTotalScore(AiAnswerScoresDTO scores) {
        int sum = scores.grammarVocabularyScore()
                + scores.naturalFluencyScore()
                + scores.scenarioAdaptationScore()
                + scores.informationCompletenessScore();
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }

    private List<String> splitArticleSegments(String text, String fieldName) {
        List<String> segments = Pattern.compile("\\n\\s*\\n")
                .splitAsStream(normalizeText(text))
                .map(String::trim)
                .toList();
        if (segments.isEmpty() || segments.size() > ARTICLE_MAX_SENTENCES
                || segments.stream().anyMatch(String::isBlank)) {
            throw invalid(fieldName + " 文章段落不合法");
        }
        return segments;
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }
}
