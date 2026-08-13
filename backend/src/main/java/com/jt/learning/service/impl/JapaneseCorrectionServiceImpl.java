package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerRecommendedExpressionDTO;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiJapaneseCorrectionErrorDTO;
import com.jt.learning.dto.AiJapaneseCorrectionReviewDTO;
import com.jt.learning.dto.JapaneseCorrectionRequest;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.JapaneseCorrectionService;
import com.jt.learning.service.ai.AiJapaneseCorrectionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.prompt.AiJapaneseCorrectionPromptBuilder;
import com.jt.learning.service.ai.validation.JapaneseCorrectionAiResponseValidator;
import com.jt.learning.vo.AnswerRecommendedExpressionVO;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.JapaneseCorrectionCommentsVO;
import com.jt.learning.vo.JapaneseCorrectionErrorVO;
import com.jt.learning.vo.JapaneseCorrectionReviewVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JapaneseCorrectionServiceImpl implements JapaneseCorrectionService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final Set<String> UNSUPPORTED_ERROR_CODES = Set.of(
            "OMISSION", "MISTRANSLATION", "ADDITION", "FALSE_FRIEND", "CHINESE_CALQUE", "PUNCTUATION"
    );

    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final AiJapaneseCorrectionPromptBuilder promptBuilder;
    private final AiJapaneseCorrectionClient correctionClient;
    private final JapaneseCorrectionAiResponseValidator responseValidator;

    public JapaneseCorrectionServiceImpl(
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            ErrorTypeMapper errorTypeMapper,
            AiJapaneseCorrectionPromptBuilder promptBuilder,
            AiJapaneseCorrectionClient correctionClient,
            JapaneseCorrectionAiResponseValidator responseValidator
    ) {
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.promptBuilder = promptBuilder;
        this.correctionClient = correctionClient;
        this.responseValidator = responseValidator;
    }

    @Override
    @Transactional
    public JapaneseCorrectionReviewVO correct(JapaneseCorrectionRequest request) {
        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本地用户不存在或不可用");
        }

        String originalText = normalizeText(request.japaneseText());
        if (originalText.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "japaneseText 不能为空");
        }
        if (originalText.length() > 5000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "japaneseText 长度不能超过 5000");
        }

        List<AiErrorTypeOptionDTO> errorTypeOptions = errorTypeMapper.selectEnabledLeafOptions().stream()
                .filter(option -> !UNSUPPORTED_ERROR_CODES.contains(option.code()))
                .toList();
        if (errorTypeOptions.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有适用于日语纠错的二级错误类型");
        }
        Map<String, AiErrorTypeOptionDTO> errorTypesByCode = errorTypeOptions.stream()
                .collect(Collectors.toMap(
                        AiErrorTypeOptionDTO::code,
                        option -> option,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        JapaneseCorrectionRequest normalizedRequest = new JapaneseCorrectionRequest(originalText);
        AiQuestionPrompt prompt = promptBuilder.build(errorTypeOptions, normalizedRequest);
        AiJapaneseCorrectionReviewDTO review = responseValidator.validate(
                correctionClient.correct(prompt, normalizedRequest),
                originalText,
                errorTypesByCode
        );
        BigDecimal totalScore = calculateTotalScore(review);
        UserAnswer userAnswer = saveReviewedCorrection(user.getId(), originalText, review, totalScore);
        return toVO(userAnswer, review, errorTypesByCode);
    }

    private UserAnswer saveReviewedCorrection(
            Long userId,
            String originalText,
            AiJapaneseCorrectionReviewDTO review,
            BigDecimal totalScore
    ) {
        LocalDateTime now = LocalDateTime.now();
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setUserId(userId);
        userAnswer.setQuestionId(null);
        userAnswer.setAnswerText(originalText);
        userAnswer.setAnswerStatus("REVIEWED");
        userAnswer.setGrammarVocabularyScore(review.scores().grammarVocabularyScore());
        userAnswer.setNaturalFluencyScore(review.scores().naturalFluencyScore());
        userAnswer.setScenarioAdaptationScore(review.scores().scenarioAdaptationScore());
        userAnswer.setInformationCompletenessScore(review.scores().informationCompletenessScore());
        userAnswer.setTotalScore(totalScore);
        userAnswer.setAiOverallComment(review.overallComment().trim());
        userAnswer.setAiRevisedText(review.correctedText().trim());
        userAnswer.setDeleted(false);
        userAnswer.setCreatedAt(now);
        userAnswer.setUpdatedAt(now);
        userAnswerMapper.insertReviewedCorrection(userAnswer);
        return userAnswer;
    }

    private BigDecimal calculateTotalScore(AiJapaneseCorrectionReviewDTO review) {
        long total = (long) review.scores().grammarVocabularyScore()
                + review.scores().naturalFluencyScore()
                + review.scores().scenarioAdaptationScore()
                + review.scores().informationCompletenessScore();
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }

    private JapaneseCorrectionReviewVO toVO(
            UserAnswer userAnswer,
            AiJapaneseCorrectionReviewDTO review,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        return new JapaneseCorrectionReviewVO(
                userAnswer.getId(),
                null,
                userAnswer.getAnswerText(),
                userAnswer.getAnswerStatus(),
                new AnswerScoresVO(
                        userAnswer.getGrammarVocabularyScore(),
                        userAnswer.getNaturalFluencyScore(),
                        userAnswer.getScenarioAdaptationScore(),
                        userAnswer.getInformationCompletenessScore()
                ),
                userAnswer.getTotalScore(),
                userAnswer.getAiOverallComment(),
                userAnswer.getAiRevisedText(),
                new JapaneseCorrectionCommentsVO(
                        review.comments().grammarVocabularyComment(),
                        review.comments().naturalFluencyComment(),
                        review.comments().styleConsistencyComment(),
                        review.comments().writingCompletenessComment()
                ),
                review.errorAnalysis().stream().map(error -> toErrorVO(error, errorTypesByCode)).toList(),
                review.revisionSuggestions(),
                review.recommendedExpressions().stream().map(this::toRecommendedExpressionVO).toList(),
                userAnswer.getCreatedAt(),
                userAnswer.getUpdatedAt()
        );
    }

    private JapaneseCorrectionErrorVO toErrorVO(
            AiJapaneseCorrectionErrorDTO error,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        AiErrorTypeOptionDTO option = errorTypesByCode.get(error.errorTypeCode());
        return new JapaneseCorrectionErrorVO(
                "CANDIDATE",
                option.id(),
                option.code(),
                option.name(),
                error.original(),
                error.issue(),
                error.suggestion(),
                error.reviewSourceText(),
                error.severity(),
                error.suggestedUserErrorTypeName(),
                error.suggestedUserErrorTypeDescription()
        );
    }

    private AnswerRecommendedExpressionVO toRecommendedExpressionVO(AiAnswerRecommendedExpressionDTO expression) {
        return new AnswerRecommendedExpressionVO(
                expression.expression(),
                expression.usage(),
                expression.formality(),
                expression.note()
        );
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
