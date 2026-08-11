package com.jt.learning.service.ai.validation;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiAnswerScoresDTO;
import com.jt.learning.dto.AiQuestionAnswerDTO;
import com.jt.learning.dto.AiReviewDTO;
import com.jt.learning.dto.AiReviewGeneratedQuestionDTO;
import com.jt.learning.dto.AiReviewQuestionResponseDTO;
import com.jt.learning.dto.AiReviewScoringResponseDTO;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReviewAiResponseValidator {

    private static final Set<String> ANSWER_TYPES = Set.of("STANDARD", "REFERENCE");

    private final ObjectMapper objectMapper;
    private final AiErrorAnalysisValidator errorAnalysisValidator;

    public ReviewAiResponseValidator(ObjectMapper objectMapper, AiErrorAnalysisValidator errorAnalysisValidator) {
        this.objectMapper = objectMapper;
        this.errorAnalysisValidator = errorAnalysisValidator;
    }

    public AiReviewDTO parseScoring(
            String content,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode,
            String answerText
    ) {
        JsonNode root = parseRoot(content, "复习评分");
        requireOnlyFields(root, List.of("review"), "复习评分 JSON 顶层");
        JsonNode reviewNode = root.get("review");
        requireOnlyFields(reviewNode,
                List.of("quality", "targetErrorResolved", "feedback", "scores", "errorAnalysis"),
                "复习评分 review");
        requireOnlyFields(reviewNode.get("scores"), List.of(
                "grammarVocabularyScore", "naturalFluencyScore",
                "scenarioAdaptationScore", "informationCompletenessScore"
        ), "复习评分 scores");
        JsonNode errorsNode = reviewNode.get("errorAnalysis");
        if (errorsNode == null || !errorsNode.isArray()) {
            throw invalid("复习评分 errorAnalysis 必须是数组");
        }
        for (int i = 0; i < errorsNode.size(); i++) {
            requireOnlyFields(errorsNode.get(i), List.of(
                    "errorTypeCode", "original", "issue", "suggestion", "severity",
                    "suggestedUserErrorTypeName", "suggestedUserErrorTypeDescription"
            ), "复习评分 errorAnalysis 项");
        }
        try {
            AiReviewDTO review = objectMapper.treeToValue(root, AiReviewScoringResponseDTO.class).review();
            validateReview(review, errorTypesByCode, answerText);
            return review;
        } catch (JacksonException exception) {
            throw invalid("复习评分输出不是合法 JSON");
        }
    }

    public AiReviewGeneratedQuestionDTO parseQuestion(String content, Set<String> existingSourceTexts) {
        JsonNode root = parseRoot(content, "复习生题");
        requireOnlyFields(root, List.of("question"), "复习生题 JSON 顶层");
        JsonNode questionNode = root.get("question");
        requireOnlyFields(questionNode, List.of("sourceText", "contextText", "grammarPoint", "answers"),
                "复习生题 question");
        JsonNode answersNode = questionNode.get("answers");
        if (answersNode == null || !answersNode.isArray()) {
            throw invalid("复习生题 answers 必须是数组");
        }
        for (int i = 0; i < answersNode.size(); i++) {
            requireOnlyFields(answersNode.get(i),
                    List.of("answerText", "answerType", "primaryAnswer", "sortOrder"),
                    "复习生题 answers 项");
        }
        try {
            AiReviewGeneratedQuestionDTO question = objectMapper
                    .treeToValue(root, AiReviewQuestionResponseDTO.class)
                    .question();
            validateQuestion(question, existingSourceTexts);
            return question;
        } catch (JacksonException exception) {
            throw invalid("复习生题输出不是合法 JSON");
        }
    }

    private JsonNode parseRoot(String content, String source) {
        if (content == null || content.isBlank()) {
            throw invalid(source + "输出为空");
        }
        try {
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isObject()) {
                throw invalid(source + " JSON 顶层必须是对象");
            }
            return root;
        } catch (JacksonException exception) {
            throw invalid(source + "输出不是合法 JSON");
        }
    }

    private void requireOnlyFields(JsonNode node, List<String> expectedFields, String label) {
        if (node == null || !node.isObject()) {
            throw invalid(label + "必须是对象");
        }
        if (!new HashSet<>(node.propertyNames()).equals(Set.copyOf(expectedFields))) {
            throw invalid(label + "字段不合法");
        }
    }

    private void validateReview(
            AiReviewDTO review,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode,
            String answerText
    ) {
        if (review == null || review.quality() == null || review.quality() < 0 || review.quality() > 5) {
            throw invalid("复习评分 quality 不合法");
        }
        if (review.targetErrorResolved() == null
                || (review.quality() < 3) == review.targetErrorResolved()) {
            throw invalid("复习评分 quality 与 targetErrorResolved 不一致");
        }
        requireText(review.feedback(), "复习评分 feedback 不能为空");
        validateScores(review.scores());
        errorAnalysisValidator.validate(review.errorAnalysis(), errorTypesByCode, answerText);
    }

    private void validateScores(AiAnswerScoresDTO scores) {
        if (scores == null) {
            throw invalid("复习评分 scores 不能为空");
        }
        validateScore(scores.grammarVocabularyScore(), "grammarVocabularyScore");
        validateScore(scores.naturalFluencyScore(), "naturalFluencyScore");
        validateScore(scores.scenarioAdaptationScore(), "scenarioAdaptationScore");
        validateScore(scores.informationCompletenessScore(), "informationCompletenessScore");
    }

    private void validateScore(Integer score, String field) {
        if (score == null || score < 0 || score > 100) {
            throw invalid("复习评分 " + field + " 不合法");
        }
    }

    private void validateQuestion(AiReviewGeneratedQuestionDTO question, Set<String> existingSourceTexts) {
        if (question == null) {
            throw invalid("复习生题 question 不能为空");
        }
        requireText(question.sourceText(), "复习生题 sourceText 不能为空");
        requireText(question.contextText(), "复习生题 contextText 不能为空");
        requireText(question.grammarPoint(), "复习生题 grammarPoint 不能为空");
        String sourceText = question.sourceText().trim();
        if (existingSourceTexts.stream().map(String::trim).anyMatch(sourceText::equals)) {
            throw invalid("复习生题题干与本周期已有题目重复");
        }
        if (question.answers() == null || question.answers().isEmpty() || question.answers().size() > 10) {
            throw invalid("复习生题 answers 数量必须在1到10之间");
        }

        Set<String> answerTexts = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        int primaryCount = 0;
        for (AiQuestionAnswerDTO answer : question.answers()) {
            if (answer == null) {
                throw invalid("复习生题答案不能为空");
            }
            requireText(answer.answerText(), "复习生题 answerText 不能为空");
            if (!ANSWER_TYPES.contains(answer.answerType())) {
                throw invalid("复习生题 answerType 不合法");
            }
            if (answer.primaryAnswer() == null || answer.sortOrder() == null || answer.sortOrder() < 0) {
                throw invalid("复习生题答案主标记或排序不合法");
            }
            if (!answerTexts.add(answer.answerText().trim()) || !sortOrders.add(answer.sortOrder())) {
                throw invalid("复习生题答案文本或排序重复");
            }
            if (answer.primaryAnswer()) {
                primaryCount++;
                if (!"STANDARD".equals(answer.answerType())) {
                    throw invalid("复习生题主答案必须是 STANDARD");
                }
            }
        }
        if (primaryCount != 1) {
            throw invalid("复习生题必须且只能有一个主答案");
        }
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
