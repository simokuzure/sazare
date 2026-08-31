package com.sazare.service.impl;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiAnswerRecommendedExpressionDTO;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.AiJapaneseCorrectionErrorDTO;
import com.sazare.dto.AiJapaneseCorrectionReviewDTO;
import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.entity.User;
import com.sazare.entity.UserAnswer;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.UserAnswerMapper;
import com.sazare.mapper.UserMapper;
import com.sazare.service.JapaneseCorrectionService;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.ai.AiJapaneseCorrectionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.prompt.AiJapaneseCorrectionPromptBuilder;
import com.sazare.service.ai.validation.JapaneseCorrectionAiResponseValidator;
import com.sazare.vo.AnswerRecommendedExpressionVO;
import com.sazare.vo.AnswerScoresVO;
import com.sazare.vo.JapaneseCorrectionCommentsVO;
import com.sazare.vo.JapaneseCorrectionErrorVO;
import com.sazare.vo.JapaneseCorrectionReviewVO;
import org.springframework.stereotype.Service;

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
    private final DictionaryCacheService dictionaryCacheService;
    private final AiJapaneseCorrectionPromptBuilder promptBuilder;
    private final AiJapaneseCorrectionClient correctionClient;
    private final JapaneseCorrectionAiResponseValidator responseValidator;

    public JapaneseCorrectionServiceImpl(
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            DictionaryCacheService dictionaryCacheService,
            AiJapaneseCorrectionPromptBuilder promptBuilder,
            AiJapaneseCorrectionClient correctionClient,
            JapaneseCorrectionAiResponseValidator responseValidator
    ) {
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.dictionaryCacheService = dictionaryCacheService;
        this.promptBuilder = promptBuilder;
        this.correctionClient = correctionClient;
        this.responseValidator = responseValidator;
    }

    @Override
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

        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        List<AiErrorTypeOptionDTO> errorTypeOptions = dictionaryCacheService.getEnabledLeafErrorTypes().stream()
                .filter(option -> !UNSUPPORTED_ERROR_CODES.contains(option.code()))
                .map(option -> new AiErrorTypeOptionDTO(
                        option.id(), option.code(),
                        direction.displayText(option.name(), option.nameEn()),
                        direction.displayText(option.description(), option.descriptionEn()),
                        option.parentCode(), direction.displayText(option.parentName(), option.parentNameEn())))
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

        JapaneseCorrectionRequest normalizedRequest = new JapaneseCorrectionRequest(originalText, request.learningMode());
        AiQuestionPrompt prompt = promptBuilder.build(errorTypeOptions, normalizedRequest);
        AiJapaneseCorrectionReviewDTO review = responseValidator.validate(
                correctionClient.correct(prompt, normalizedRequest),
                originalText,
                errorTypesByCode
        );
        BigDecimal totalScore = calculateTotalScore(review);
        UserAnswer userAnswer = saveReviewedCorrection(
                user.getId(), originalText, normalizedRequest.learningMode(), review, totalScore);
        return toVO(userAnswer, review, errorTypesByCode);
    }

    private UserAnswer saveReviewedCorrection(
            Long userId,
            String originalText,
            String learningMode,
            AiJapaneseCorrectionReviewDTO review,
            BigDecimal totalScore
    ) {
        LocalDateTime now = LocalDateTime.now();
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setUserId(userId);
        userAnswer.setQuestionId(null);
        userAnswer.setLearningMode(learningMode);
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
