package com.jt.learning.service.impl;

import com.jt.learning.common.TranslationDirection;
import com.jt.learning.dto.AiAnswerErrorAnalysisDTO;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiQuestionAnswerDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.dto.AiReviewDTO;
import com.jt.learning.dto.AiReviewGeneratedQuestionDTO;
import com.jt.learning.dto.ReviewAttemptHistoryRow;
import com.jt.learning.dto.ReviewAttemptRequest;
import com.jt.learning.dto.ReviewCardListRow;
import com.jt.learning.dto.ReviewCardQueryRequest;
import com.jt.learning.dto.ReviewCycleProgressRow;
import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.ReviewAttempt;
import com.jt.learning.entity.ReviewCard;
import com.jt.learning.entity.ReviewCycle;
import com.jt.learning.entity.ReviewCycleQuestion;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.entity.UserErrorType;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.ReviewAttemptMapper;
import com.jt.learning.mapper.ReviewCardMapper;
import com.jt.learning.mapper.ReviewCycleMapper;
import com.jt.learning.mapper.ReviewCycleQuestionMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserErrorTypeMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.AiReviewQuestionClient;
import com.jt.learning.service.ai.AiReviewScoringClient;
import com.jt.learning.service.ai.prompt.AiReviewQuestionPromptBuilder;
import com.jt.learning.service.ai.prompt.AiReviewScoringPromptBuilder;
import com.jt.learning.service.ai.validation.ReviewAiResponseValidator;
import com.jt.learning.service.ReviewService;
import com.jt.learning.service.DictionaryCacheService;
import com.jt.learning.service.review.Sm2Result;
import com.jt.learning.service.review.Sm2Scheduler;
import com.jt.learning.util.ReviewDueAtCalculator;
import com.jt.learning.vo.AnswerErrorAnalysisVO;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.ReviewAttemptHistoryVO;
import com.jt.learning.vo.ReviewAttemptVO;
import com.jt.learning.vo.ReviewCardDetailVO;
import com.jt.learning.vo.ReviewCardListVO;
import com.jt.learning.vo.ReviewCycleProgressVO;
import com.jt.learning.vo.ReviewDerivedQuestionGenerationVO;
import com.jt.learning.vo.ReviewQuestionVO;
import com.jt.learning.vo.TagVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jt.learning.util.TagHierarchyUtils.secondLevelTags;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);
    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String CARD_ACTIVE = "ACTIVE";
    private static final String CARD_MASTERED = "MASTERED";
    private static final String CYCLE_IN_PROGRESS = "IN_PROGRESS";
    private static final String QUESTION_ORIGINAL = "ORIGINAL";
    private static final String QUESTION_DERIVED = "DERIVED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RETRY = "RETRY";
    private static final String STATUS_PASSED = "PASSED";
    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String SOURCE_REVIEW_DERIVED = "REVIEW_DERIVED";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";

    private final ReviewCardMapper reviewCardMapper;
    private final ReviewCycleMapper reviewCycleMapper;
    private final ReviewCycleQuestionMapper reviewCycleQuestionMapper;
    private final ReviewAttemptMapper reviewAttemptMapper;
    private final UserMapper userMapper;
    private final UserErrorTypeMapper userErrorTypeMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final DictionaryCacheService dictionaryCacheService;
    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionTagMapper questionTagMapper;
    private final TagMapper tagMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final Sm2Scheduler sm2Scheduler;
    private final AiReviewScoringPromptBuilder scoringPromptBuilder;
    private final AiReviewQuestionPromptBuilder questionPromptBuilder;
    private final AiReviewScoringClient scoringClient;
    private final AiReviewQuestionClient questionClient;
    private final ReviewAiResponseValidator aiResponseValidator;

    public ReviewServiceImpl(
            ReviewCardMapper reviewCardMapper,
            ReviewCycleMapper reviewCycleMapper,
            ReviewCycleQuestionMapper reviewCycleQuestionMapper,
            ReviewAttemptMapper reviewAttemptMapper,
            UserMapper userMapper,
            UserErrorTypeMapper userErrorTypeMapper,
            ErrorTypeMapper errorTypeMapper,
            DictionaryCacheService dictionaryCacheService,
            QuestionMapper questionMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionTagMapper questionTagMapper,
            TagMapper tagMapper,
            UserAnswerMapper userAnswerMapper,
            Sm2Scheduler sm2Scheduler,
            AiReviewScoringPromptBuilder scoringPromptBuilder,
            AiReviewQuestionPromptBuilder questionPromptBuilder,
            AiReviewScoringClient scoringClient,
            AiReviewQuestionClient questionClient,
            ReviewAiResponseValidator aiResponseValidator
    ) {
        this.reviewCardMapper = reviewCardMapper;
        this.reviewCycleMapper = reviewCycleMapper;
        this.reviewCycleQuestionMapper = reviewCycleQuestionMapper;
        this.reviewAttemptMapper = reviewAttemptMapper;
        this.userMapper = userMapper;
        this.userErrorTypeMapper = userErrorTypeMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.dictionaryCacheService = dictionaryCacheService;
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionTagMapper = questionTagMapper;
        this.tagMapper = tagMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.sm2Scheduler = sm2Scheduler;
        this.scoringPromptBuilder = scoringPromptBuilder;
        this.questionPromptBuilder = questionPromptBuilder;
        this.scoringClient = scoringClient;
        this.questionClient = questionClient;
        this.aiResponseValidator = aiResponseValidator;
    }

    @Override
    public PageVO<ReviewCardListVO> listReviewCards(ReviewCardQueryRequest request) {
        User user = requireLocalUser();
        LocalDateTime now = LocalDateTime.now();
        long total = reviewCardMapper.countCards(
                user.getId(), request.status(), request.learningMode(), request.dueOnly(), now);
        if (total == 0) {
            return new PageVO<>(List.of(), request.page(), request.size(), 0);
        }
        long offset = (long) (request.page() - 1) * request.size();
        List<ReviewCardListVO> items = reviewCardMapper.selectCardList(
                        user.getId(), request.status(), request.learningMode(),
                        request.dueOnly(), now, request.size(), offset)
                .stream()
                .map(this::toCardListVO)
                .toList();
        return new PageVO<>(items, request.page(), request.size(), total);
    }

    @Override
    public ReviewCardDetailVO getReviewCard(Long cardId, boolean earlyReview) {
        User user = requireLocalUser();
        ReviewCard card = requireCard(reviewCardMapper.selectByIdAndUserId(cardId, user.getId()));
        UserErrorType userErrorType = requireUserErrorType(card, user.getId());
        ErrorType errorType = requireErrorType(userErrorType.getErrorTypeId());
        ReviewCycle cycle = reviewCycleMapper.selectLatestByCardId(card.getId());
        ReviewCycleProgressRow progress = cycle == null ? emptyProgress() : loadProgress(cycle);

        String state;
        ReviewQuestionVO question = null;
        if (CARD_MASTERED.equals(card.getStatus())) {
            state = "MASTERED";
        } else if (!earlyReview && card.getDueAt().isAfter(LocalDateTime.now())) {
            state = "WAITING";
        } else {
            ReviewCycleQuestion selected = selectCurrentQuestion(cycle);
            if (selected == null) {
                state = "DERIVED_GENERATION_REQUIRED";
            } else {
                state = "READY";
                question = toReviewQuestionVO(selected);
            }
        }
        List<ReviewAttemptHistoryVO> reviewAttempts = reviewAttemptMapper
                .selectReviewHistory(card.getId(), user.getId())
                .stream()
                .map(this::toReviewAttemptHistoryVO)
                .toList();
        return new ReviewCardDetailVO(
                card.getId(), card.getUserErrorTypeId(), userErrorType.getName(), userErrorType.getDescription(),
                errorType.getId(), errorType.getCode(), errorType.getName(), card.getStatus(), card.getEaseFactor(),
                card.getRepetitionCount(), card.getIntervalDays(), card.getLapseCount(), card.getDueAt(),
                card.getLastReviewedAt(), card.getMasteredAt(), state, toProgressVO(cycle, progress), question,
                reviewAttempts
        );
    }

    @Override
    @Transactional
    public void deleteReviewCard(Long cardId) {
        User user = requireLocalUser();
        LocalDateTime now = LocalDateTime.now();
        ReviewCard card = requireCard(reviewCardMapper.selectForUpdateByIdAndUserId(cardId, user.getId()));
        if (reviewCardMapper.logicalDelete(card.getId(), user.getId(), now) != 1
                || userErrorTypeMapper.archiveByIdAndUserId(card.getUserErrorTypeId(), user.getId(), now) != 1) {
            throw business("复习卡片不存在或已删除");
        }
    }

    @Override
    @Transactional
    public ReviewAttemptVO submitReviewAttempt(Long cardId, ReviewAttemptRequest request) {
        User user = requireLocalUser();
        LocalDateTime now = LocalDateTime.now();
        ReviewCard card = requireCard(reviewCardMapper.selectForUpdateByIdAndUserId(cardId, user.getId()));
        requireReadyCard(card, now, request.earlyReview());
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        ReviewCycleQuestion cycleQuestion = reviewCycleQuestionMapper
                .selectByIdAndCycleId(request.cycleQuestionId(), cycle.getId());
        validateAttemptVersionAndEligibility(cycle, cycleQuestion, request.expectedAttemptCount());

        Question question = requireQuestion(cycleQuestion.getQuestionId());
        List<QuestionAnswer> standardAnswers = requireAnswers(question.getId());
        UserErrorType userErrorType = requireUserErrorType(card, user.getId());
        TranslationDirection direction = TranslationDirection.fromLearningMode(userErrorType.getLearningMode());
        ErrorType errorType = requireErrorType(userErrorType.getErrorTypeId());
        List<AiErrorTypeOptionDTO> errorTypeOptions = localizeErrorTypes(
                dictionaryCacheService.getEnabledLeafErrorTypes(), direction);
        Map<String, AiErrorTypeOptionDTO> errorTypesByCode = errorTypeOptions.stream()
                .collect(Collectors.toMap(AiErrorTypeOptionDTO::code, Function.identity()));

        AiReviewDTO review;
        try {
            AiQuestionPrompt prompt = scoringPromptBuilder.build(
                    userErrorType, errorType, question, standardAnswers, errorTypeOptions, request.answerText());
            review = aiResponseValidator.parseScoring(
                    scoringClient.scoreAnswer(prompt), errorTypesByCode, request.answerText().trim());
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "复习 AI 评分失败");
        }

        BigDecimal totalScore = calculateTotalScore(review);
        UserAnswer userAnswer = createSubmittedAnswer(
                user.getId(), question.getId(), userErrorType.getLearningMode(), request.answerText().trim(), now);
        userAnswerMapper.updateReviewed(
                userAnswer.getId(),
                review.scores().grammarVocabularyScore(),
                review.scores().naturalFluencyScore(),
                review.scores().scenarioAdaptationScore(),
                review.scores().informationCompletenessScore(),
                totalScore,
                review.feedback().trim(),
                now
        );
        boolean passed = review.quality() >= 3;
        int successfulCount = cycle.getSuccessfulReviewCount() + (passed ? 1 : 0);
        int failedCount = cycle.getFailedReviewCount() + (passed ? 0 : 1);
        reviewCycleQuestionMapper.markAttempt(
                cycleQuestion.getId(), passed ? STATUS_PASSED : STATUS_RETRY,
                cycleQuestion.getAttemptCount() + 1, review.quality(), now, passed ? now : null, now);
        reviewCycleMapper.updateProgress(
                cycle.getId(), cycle.getTargetSuccessCount(), successfulCount, failedCount,
                cycle.getVerificationRequiredAfter(), now);
        cycle.setSuccessfulReviewCount(successfulCount);
        cycle.setFailedReviewCount(failedCount);

        Sm2Result sm2 = sm2Scheduler.schedule(
                card.getEaseFactor(), card.getRepetitionCount(), card.getIntervalDays(), card.getLapseCount(),
                review.quality());
        ReviewCycleProgressRow progress = loadProgress(cycle);
        boolean completed = isCycleComplete(cycle, progress);
        LocalDateTime nextDueAt = completed ? null : ReviewDueAtCalculator.calculate(now, sm2.intervalDays());
        updateCardAfterAttempt(card, sm2, completed, nextDueAt, now);
        if (completed) {
            reviewCycleMapper.completeCycle(cycle.getId(), now);
        }

        ReviewAttempt attempt = buildAttempt(
                user.getId(), card.getId(), cycle.getId(), cycleQuestion.getId(), userAnswer.getId(),
                "REVIEW", passed ? RESULT_PASS : RESULT_FAIL, review.quality(), passed, review.feedback(),
                sm2, successfulCount, nextDueAt, now);
        reviewAttemptMapper.insertAttempt(attempt);

        String generationStatus = "NOT_REQUIRED";
        if (!completed && requiresDerivedQuestion(cycle, progress)) {
            try {
                generateDerivedQuestionLocked(card, cycle, userErrorType, errorType, now);
                generationStatus = "SUCCEEDED";
                progress = loadProgress(cycle);
            } catch (BusinessException exception) {
                log.error(
                        "复习衍生题生成失败: cardId={}, cycleId={}",
                        card.getId(),
                        cycle.getId(),
                        exception
                );
                generationStatus = "FAILED";
            }
        }

        return new ReviewAttemptVO(
                userAnswer.getId(), review.quality(), passed ? RESULT_PASS : RESULT_FAIL,
                review.targetErrorResolved(), review.feedback(), new AnswerScoresVO(
                        review.scores().grammarVocabularyScore(),
                        review.scores().naturalFluencyScore(),
                        review.scores().scenarioAdaptationScore(),
                        review.scores().informationCompletenessScore()
                ), totalScore,
                toErrorAnalysisVO(review.errorAnalysis(), errorTypesByCode),
                toProgressVO(cycle, progress), nextDueAt, completed ? CARD_MASTERED : CARD_ACTIVE,
                standardAnswers.stream().map(this::toAnswerVO).toList(), generationStatus
        );
    }

    private BigDecimal calculateTotalScore(AiReviewDTO review) {
        int sum = review.scores().grammarVocabularyScore()
                + review.scores().naturalFluencyScore()
                + review.scores().scenarioAdaptationScore()
                + review.scores().informationCompletenessScore();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public ReviewDerivedQuestionGenerationVO generateDerivedQuestion(Long cardId) {
        User user = requireLocalUser();
        ReviewCard card = requireCard(reviewCardMapper.selectForUpdateByIdAndUserId(cardId, user.getId()));
        if (!CARD_ACTIVE.equals(card.getStatus())) {
            throw business("已掌握卡片不能生成衍生题");
        }
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        UserErrorType userErrorType = requireUserErrorType(card, user.getId());
        ErrorType errorType = requireErrorType(userErrorType.getErrorTypeId());
        GeneratedQuestionIds ids = generateDerivedQuestionLocked(
                card, cycle, userErrorType, errorType, LocalDateTime.now());
        return new ReviewDerivedQuestionGenerationVO(ids.questionId(), ids.cycleQuestionId(), "SUCCEEDED");
    }

    @Override
    @Transactional
    public ReviewCard recordPracticeError(
            Long userId,
            Long userAnswerId,
            Long questionId,
            Long userErrorTypeId,
            LocalDateTime occurredAt
    ) {
        UserErrorType userErrorType = userErrorTypeMapper.selectActiveByIdAndUserId(userErrorTypeId, userId);
        if (userErrorType == null) {
            throw business("用户错误类型不存在或不属于当前用户");
        }
        requireQuestion(questionId);

        ReviewCard card = reviewCardMapper.selectForUpdateByUserErrorTypeId(userErrorTypeId);
        boolean newCard = false;
        if (card == null) {
            ReviewCard candidate = newReviewCard(userId, userErrorTypeId, occurredAt);
            newCard = reviewCardMapper.insertCardIfAbsent(candidate) == 1;
            card = reviewCardMapper.selectForUpdateByUserErrorTypeId(userErrorTypeId);
        }
        if (card == null || !card.getUserId().equals(userId)) {
            throw business("复习卡片归属不合法");
        }
        if (reviewAttemptMapper.existsByCardIdAndUserAnswerId(card.getId(), userAnswerId)) {
            return card;
        }

        if (newCard) {
            createPracticeFailureCycle(card, userAnswerId, questionId, 1, 0, occurredAt);
            return card;
        }
        if (CARD_MASTERED.equals(card.getStatus())) {
            int cycleNo = reviewCycleMapper.selectMaxCycleNo(card.getId()) + 1;
            createPracticeFailureCycle(card, userAnswerId, questionId, cycleNo, 1, occurredAt);
            return card;
        }
        applyPracticeFailureToCurrentCycle(card, userAnswerId, questionId, occurredAt);
        return card;
    }

    @Override
    @Transactional
    public void recordPracticeErrors(
            Long userId,
            Long userAnswerId,
            List<Long> questionIds,
            Long userErrorTypeId,
            LocalDateTime occurredAt
    ) {
        List<Long> distinctQuestionIds = questionIds.stream().distinct().toList();
        if (distinctQuestionIds.isEmpty()) {
            return;
        }
        distinctQuestionIds.forEach(this::requireQuestion);
        recordPracticeError(
                userId,
                userAnswerId,
                distinctQuestionIds.getFirst(),
                userErrorTypeId,
                occurredAt
        );
        if (distinctQuestionIds.size() == 1) {
            return;
        }

        ReviewCard card = reviewCardMapper.selectForUpdateByUserErrorTypeId(userErrorTypeId);
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        for (Long questionId : distinctQuestionIds.subList(1, distinctQuestionIds.size())) {
            ReviewCycleQuestion cycleQuestion = newOriginalRetryQuestion(cycle.getId(), questionId, occurredAt);
            reviewCycleQuestionMapper.insertQuestionIfAbsent(cycleQuestion);
        }
        reviewCycleMapper.updateProgress(
                cycle.getId(),
                4,
                cycle.getSuccessfulReviewCount(),
                cycle.getFailedReviewCount(),
                occurredAt,
                occurredAt
        );
    }

    private void createPracticeFailureCycle(
            ReviewCard card,
            Long userAnswerId,
            Long questionId,
            int cycleNo,
            int initialFailureCount,
            LocalDateTime occurredAt
    ) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setReviewCardId(card.getId());
        cycle.setCycleNo(cycleNo);
        cycle.setStatus(CYCLE_IN_PROGRESS);
        cycle.setTargetSuccessCount(4);
        cycle.setSuccessfulReviewCount(0);
        cycle.setFailedReviewCount(initialFailureCount);
        cycle.setVerificationRequiredAfter(occurredAt);
        cycle.setStartedAt(occurredAt);
        cycle.setCompletedAt(null);
        cycle.setCreatedAt(occurredAt);
        cycle.setUpdatedAt(occurredAt);
        reviewCycleMapper.insertCycle(cycle);

        ReviewCycleQuestion cycleQuestion = newOriginalRetryQuestion(cycle.getId(), questionId, occurredAt);
        reviewCycleQuestionMapper.insertQuestion(cycleQuestion);
        applyPracticeFailureSchedule(card, cycle, cycleQuestion, userAnswerId, occurredAt);
    }

    private void applyPracticeFailureToCurrentCycle(
            ReviewCard card,
            Long userAnswerId,
            Long questionId,
            LocalDateTime occurredAt
    ) {
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        ReviewCycleQuestion cycleQuestion = reviewCycleQuestionMapper
                .selectByCycleIdAndQuestionId(cycle.getId(), questionId);
        if (cycleQuestion == null) {
            cycleQuestion = newOriginalRetryQuestion(cycle.getId(), questionId, occurredAt);
            reviewCycleQuestionMapper.insertQuestionIfAbsent(cycleQuestion);
            cycleQuestion = reviewCycleQuestionMapper.selectByCycleIdAndQuestionId(cycle.getId(), questionId);
        } else {
            reviewCycleQuestionMapper.markPracticeFailure(
                    cycleQuestion.getId(), cycleQuestion.getAttemptCount() + 1, occurredAt);
            cycleQuestion.setAttemptCount(cycleQuestion.getAttemptCount() + 1);
        }
        int failedCount = cycle.getFailedReviewCount() + 1;
        reviewCycleMapper.updateProgress(
                cycle.getId(), 4, cycle.getSuccessfulReviewCount(), failedCount, occurredAt, occurredAt);
        cycle.setTargetSuccessCount(4);
        cycle.setFailedReviewCount(failedCount);
        cycle.setVerificationRequiredAfter(occurredAt);
        applyPracticeFailureSchedule(card, cycle, cycleQuestion, userAnswerId, occurredAt);
    }

    private void applyPracticeFailureSchedule(
            ReviewCard card,
            ReviewCycle cycle,
            ReviewCycleQuestion cycleQuestion,
            Long userAnswerId,
            LocalDateTime occurredAt
    ) {
        Sm2Result sm2 = sm2Scheduler.schedule(
                card.getEaseFactor(), card.getRepetitionCount(), card.getIntervalDays(), card.getLapseCount(), 2);
        LocalDateTime dueAt = ReviewDueAtCalculator.calculate(occurredAt, 1);
        reviewCardMapper.updateSchedule(
                card.getId(), CARD_ACTIVE, sm2.easeFactor(), sm2.repetitionCount(), sm2.intervalDays(),
                sm2.lapseCount(), dueAt, occurredAt, null, occurredAt);
        reviewAttemptMapper.insertAttempt(buildAttempt(
                card.getUserId(), card.getId(), cycle.getId(), cycleQuestion.getId(), userAnswerId,
                "PRACTICE_ERROR", RESULT_FAIL, 2, false, null, sm2,
                cycle.getSuccessfulReviewCount(), dueAt, occurredAt));
    }

    private GeneratedQuestionIds generateDerivedQuestionLocked(
            ReviewCard card,
            ReviewCycle cycle,
            UserErrorType userErrorType,
            ErrorType errorType,
            LocalDateTime now
    ) {
        ReviewCycleProgressRow progress = loadProgress(cycle);
        if (!requiresDerivedQuestion(cycle, progress)) {
            throw business("当前周期不需要生成衍生题");
        }

        List<ReviewCycleQuestion> cycleQuestions = reviewCycleQuestionMapper.selectAllByCycleId(cycle.getId());
        List<Long> questionIds = cycleQuestions.stream().map(ReviewCycleQuestion::getQuestionId).toList();
        List<Question> questions = questionMapper.selectQuestionsByIds(questionIds);
        Map<Long, Question> questionsById = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        List<QuestionAnswer> answers = questionAnswerMapper.selectActiveAnswersByQuestionIds(questionIds);
        Map<Long, List<QuestionAnswer>> answersByQuestionId = answers.stream()
                .collect(Collectors.groupingBy(QuestionAnswer::getQuestionId, LinkedHashMap::new, Collectors.toList()));
        List<Tag> sceneTags = secondLevelTags(dictionaryCacheService.getEnabledTagsByType(TAG_TYPE_SCENE));
        List<Tag> functionTags = secondLevelTags(dictionaryCacheService.getEnabledTagsByType(TAG_TYPE_FUNCTION));
        Map<String, Tag> allowedTagsByCode = java.util.stream.Stream.concat(
                        sceneTags.stream(), functionTags.stream())
                .collect(Collectors.toMap(Tag::getCode, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        TranslationDirection direction = TranslationDirection.fromLearningMode(userErrorType.getLearningMode());
        List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags, direction);
        List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags, direction);
        AiQuestionPrompt prompt = questionPromptBuilder.build(
                userErrorType, errorType, questions, answersByQuestionId, sceneTagOptions, functionTagOptions);
        Set<String> sourceTexts = questions.stream().map(Question::getSourceText).collect(Collectors.toSet());
        AiReviewGeneratedQuestionDTO generated = aiResponseValidator.parseQuestion(
                questionClient.generateQuestion(prompt, sceneTagOptions, functionTagOptions),
                sourceTexts,
                sceneTags.stream().map(Tag::getCode).collect(Collectors.toSet()),
                allowedTagsByCode.keySet());

        ReviewCycleQuestion baseCycleQuestion = reviewCycleQuestionMapper.selectLatestFailedOriginal(cycle.getId());
        if (baseCycleQuestion == null) {
            baseCycleQuestion = cycleQuestions.stream()
                    .filter(item -> QUESTION_ORIGINAL.equals(item.getQuestionRole()))
                    .max(Comparator.comparing(
                            ReviewCycleQuestion::getLastAttemptAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow(() -> business("当前周期缺少原题"));
        }
        Question baseQuestion = questionsById.get(baseCycleQuestion.getQuestionId());
        if (baseQuestion == null) {
            throw business("复习原题不存在");
        }

        Question derived = newDerivedQuestion(baseQuestion, generated, now);
        questionMapper.insertQuestion(derived);
        for (AiQuestionAnswerDTO answerDTO : generated.answers()) {
            questionAnswerMapper.insertQuestionAnswer(newDerivedAnswer(derived.getId(), answerDTO, now));
        }
        for (String tagCode : generated.tagCodes()) {
            questionTagMapper.insertQuestionTag(derived.getId(), allowedTagsByCode.get(tagCode.trim()).getId());
        }

        ReviewCycleQuestion cycleQuestion = new ReviewCycleQuestion();
        cycleQuestion.setReviewCycleId(cycle.getId());
        cycleQuestion.setQuestionId(derived.getId());
        cycleQuestion.setQuestionRole(QUESTION_DERIVED);
        cycleQuestion.setReviewStatus(STATUS_PENDING);
        cycleQuestion.setAttemptCount(0);
        cycleQuestion.setLastQuality(null);
        cycleQuestion.setLastAttemptAt(null);
        cycleQuestion.setPassedAt(null);
        cycleQuestion.setSortOrder(cycleQuestions.stream()
                .map(ReviewCycleQuestion::getSortOrder).max(Integer::compareTo).orElse(-1) + 1);
        cycleQuestion.setCreatedAt(now);
        cycleQuestion.setUpdatedAt(now);
        reviewCycleQuestionMapper.insertQuestion(cycleQuestion);
        return new GeneratedQuestionIds(derived.getId(), cycleQuestion.getId());
    }

    private boolean requiresDerivedQuestion(ReviewCycle cycle, ReviewCycleProgressRow progress) {
        boolean allOriginalsPassed = progress.getOriginalQuestionCount() > 0
                && progress.getOriginalQuestionCount().equals(progress.getOriginalPassedCount());
        boolean noUnfinishedQuestion = progress.getActiveQuestionCount() == 0;
        return allOriginalsPassed && noUnfinishedQuestion && netSuccessCount(cycle) < cycle.getTargetSuccessCount();
    }

    private boolean isCycleComplete(ReviewCycle cycle, ReviewCycleProgressRow progress) {
        return progress.getOriginalQuestionCount() > 0
                && progress.getOriginalQuestionCount().equals(progress.getOriginalPassedCount())
                && netSuccessCount(cycle) >= cycle.getTargetSuccessCount()
                && progress.getActiveQuestionCount() == 0;
    }

    private int netSuccessCount(ReviewCycle cycle) {
        return cycle.getSuccessfulReviewCount() - cycle.getFailedReviewCount();
    }

    private void validateAttemptVersionAndEligibility(
            ReviewCycle cycle,
            ReviewCycleQuestion requested,
            int expectedAttemptCount
    ) {
        if (requested == null || STATUS_PASSED.equals(requested.getReviewStatus())) {
            throw business("复习题不存在或当前不可作答");
        }
        if (requested.getAttemptCount() != expectedAttemptCount) {
            throw business("复习题版本已过期，请刷新后重试");
        }
        ReviewCycleQuestion retry = reviewCycleQuestionMapper.selectLatestRetry(cycle.getId());
        if (retry != null && !retry.getId().equals(requested.getId())) {
            throw business("请先重试最近答错的题目");
        }
        if (retry != null) {
            return;
        }
        if (QUESTION_ORIGINAL.equals(requested.getQuestionRole()) && STATUS_PENDING.equals(requested.getReviewStatus())) {
            return;
        }
        ReviewCycleQuestion pendingOriginal = reviewCycleQuestionMapper.selectRandomPendingOriginal(cycle.getId());
        ReviewCycleQuestion activeDerived = reviewCycleQuestionMapper.selectActiveDerived(cycle.getId());
        if (pendingOriginal != null || activeDerived == null || !activeDerived.getId().equals(requested.getId())) {
            throw business("复习题当前不可作答");
        }
    }

    private ReviewCycleQuestion selectCurrentQuestion(ReviewCycle cycle) {
        if (cycle == null || !CYCLE_IN_PROGRESS.equals(cycle.getStatus())) {
            return null;
        }
        ReviewCycleQuestion question = reviewCycleQuestionMapper.selectLatestRetry(cycle.getId());
        if (question != null) {
            return question;
        }
        question = reviewCycleQuestionMapper.selectRandomPendingOriginal(cycle.getId());
        return question != null ? question : reviewCycleQuestionMapper.selectActiveDerived(cycle.getId());
    }

    private ReviewQuestionVO toReviewQuestionVO(ReviewCycleQuestion cycleQuestion) {
        Question question = requireQuestion(cycleQuestion.getQuestionId());
        List<TagVO> tags = tagMapper.selectEnabledTagsByQuestionId(question.getId()).stream()
                .map(this::toTagVO)
                .toList();
        return new ReviewQuestionVO(
                cycleQuestion.getId(), question.getId(), cycleQuestion.getQuestionRole(), question.getSourceText(),
                question.getContextText(), question.getLevel(), question.getDifficulty(), question.getGrammarPoint(),
                question.getSpoken(), question.getBusiness(), question.getExam(), tags, cycleQuestion.getAttemptCount());
    }

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags, TranslationDirection direction) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(tag.getCode(),
                        direction.displayText(tag.getName(), tag.getNameEn()),
                        direction.displayText(tag.getDescription(), tag.getDescriptionEn())))
                .toList();
    }

    private List<AiErrorTypeOptionDTO> localizeErrorTypes(
            List<AiErrorTypeOptionDTO> options,
            TranslationDirection direction
    ) {
        return options.stream().map(option -> new AiErrorTypeOptionDTO(
                option.id(), option.code(),
                direction.displayText(option.name(), option.nameEn()),
                direction.displayText(option.description(), option.descriptionEn()),
                option.parentCode(), direction.displayText(option.parentName(), option.parentNameEn())
        )).toList();
    }

    private ReviewCardListVO toCardListVO(ReviewCardListRow row) {
        ReviewCycleProgressVO progress = new ReviewCycleProgressVO(
                row.getCycleNo(), row.getSuccessfulReviewCount(), row.getFailedReviewCount(),
                row.getSuccessfulReviewCount() - row.getFailedReviewCount(), row.getTargetSuccessCount(),
                row.getOriginalQuestionCount(), row.getOriginalPassedCount(), row.getRetryQuestionCount(),
                row.getPendingQuestionCount());
        return new ReviewCardListVO(
                row.getId(), row.getUserErrorTypeId(), row.getErrorTypeId(), row.getErrorTypeCode(),
                row.getErrorTypeName(), row.getUserErrorTypeName(), row.getUserErrorTypeDescription(),
                row.getStatus(), row.getDueAt(), progress, row.getLastReviewedAt(), row.getMasteredAt());
    }

    private ReviewCycleProgressVO toProgressVO(ReviewCycle cycle, ReviewCycleProgressRow progress) {
        if (cycle == null) {
            return null;
        }
        return new ReviewCycleProgressVO(
                cycle.getCycleNo(), cycle.getSuccessfulReviewCount(), cycle.getFailedReviewCount(),
                netSuccessCount(cycle), cycle.getTargetSuccessCount(),
                progress.getOriginalQuestionCount(), progress.getOriginalPassedCount(),
                progress.getRetryQuestionCount(), progress.getPendingQuestionCount());
    }

    private ReviewCycleProgressRow loadProgress(ReviewCycle cycle) {
        return reviewCycleQuestionMapper.selectProgress(cycle.getId(), cycle.getVerificationRequiredAfter());
    }

    private ReviewCycleProgressRow emptyProgress() {
        ReviewCycleProgressRow progress = new ReviewCycleProgressRow();
        progress.setOriginalQuestionCount(0);
        progress.setOriginalPassedCount(0);
        progress.setRetryQuestionCount(0);
        progress.setPendingQuestionCount(0);
        progress.setActiveQuestionCount(0);
        progress.setDerivedQuestionCount(0);
        progress.setVerifiedDerivedPassedCount(0);
        return progress;
    }

    private void requireReadyCard(ReviewCard card, LocalDateTime now, boolean earlyReview) {
        if (!CARD_ACTIVE.equals(card.getStatus())) {
            throw business("复习卡片已掌握");
        }
        if (card.getDueAt() == null || (!earlyReview && card.getDueAt().isAfter(now))) {
            throw business("复习卡片尚未到期");
        }
    }

    private ReviewCycle requireCurrentCycle(Long cardId) {
        ReviewCycle cycle = reviewCycleMapper.selectCurrentForUpdateByCardId(cardId);
        if (cycle == null) {
            throw business("复习卡片没有进行中的周期");
        }
        return cycle;
    }

    private User requireLocalUser() {
        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw business("默认用户不存在或未启用");
        }
        return user;
    }

    private ReviewCard requireCard(ReviewCard card) {
        if (card == null) {
            throw business("复习卡片不存在或已删除");
        }
        return card;
    }

    private UserErrorType requireUserErrorType(ReviewCard card, Long userId) {
        UserErrorType type = userErrorTypeMapper.selectActiveByIdAndUserId(card.getUserErrorTypeId(), userId);
        if (type == null) {
            throw business("用户错误类型不存在或未启用");
        }
        return type;
    }

    private ErrorType requireErrorType(Long errorTypeId) {
        ErrorType type = errorTypeMapper.selectEnabledLeafById(errorTypeId);
        if (type == null) {
            throw business("错误类型不存在或未启用");
        }
        return type;
    }

    private Question requireQuestion(Long questionId) {
        Question question = questionMapper.selectActiveQuestionById(questionId);
        if (question == null) {
            throw business("题目不存在或未启用");
        }
        return question;
    }

    private List<QuestionAnswer> requireAnswers(Long questionId) {
        List<QuestionAnswer> answers = questionAnswerMapper.selectActiveAnswersByQuestionId(questionId);
        if (answers.isEmpty()) {
            throw business("题目没有可用答案");
        }
        return answers;
    }

    private UserAnswer createSubmittedAnswer(
            Long userId,
            Long questionId,
            String learningMode,
            String answerText,
            LocalDateTime now
    ) {
        UserAnswer answer = new UserAnswer();
        answer.setUserId(userId);
        answer.setQuestionId(questionId);
        answer.setLearningMode(learningMode);
        answer.setAnswerText(answerText);
        answer.setAnswerStatus("SUBMITTED");
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        userAnswerMapper.insertUserAnswer(answer);
        return answer;
    }

    private void updateCardAfterAttempt(
            ReviewCard card,
            Sm2Result sm2,
            boolean completed,
            LocalDateTime dueAt,
            LocalDateTime now
    ) {
        reviewCardMapper.updateSchedule(
                card.getId(), completed ? CARD_MASTERED : CARD_ACTIVE, sm2.easeFactor(), sm2.repetitionCount(),
                sm2.intervalDays(), sm2.lapseCount(), dueAt, now, completed ? now : null, now);
    }

    private ReviewCard newReviewCard(Long userId, Long userErrorTypeId, LocalDateTime now) {
        ReviewCard card = new ReviewCard();
        card.setUserId(userId);
        card.setUserErrorTypeId(userErrorTypeId);
        card.setStatus(CARD_ACTIVE);
        card.setEaseFactor(new BigDecimal("2.5000"));
        card.setRepetitionCount(0);
        card.setIntervalDays(1);
        card.setLapseCount(0);
        card.setDueAt(ReviewDueAtCalculator.calculate(now, card.getIntervalDays()));
        card.setLastReviewedAt(null);
        card.setMasteredAt(null);
        card.setDeleted(false);
        card.setCreatedAt(now);
        card.setUpdatedAt(now);
        return card;
    }

    private ReviewCycleQuestion newOriginalRetryQuestion(Long cycleId, Long questionId, LocalDateTime now) {
        ReviewCycleQuestion question = new ReviewCycleQuestion();
        question.setReviewCycleId(cycleId);
        question.setQuestionId(questionId);
        question.setQuestionRole(QUESTION_ORIGINAL);
        question.setReviewStatus(STATUS_RETRY);
        question.setAttemptCount(1);
        question.setLastQuality(2);
        question.setLastAttemptAt(now);
        question.setPassedAt(null);
        question.setSortOrder(0);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        return question;
    }

    private ReviewAttempt buildAttempt(
            Long userId,
            Long cardId,
            Long cycleId,
            Long cycleQuestionId,
            Long userAnswerId,
            String source,
            String result,
            int quality,
            boolean resolved,
            String feedback,
            Sm2Result sm2,
            int cycleSuccessCount,
            LocalDateTime nextDueAt,
            LocalDateTime now
    ) {
        ReviewAttempt attempt = new ReviewAttempt();
        attempt.setUserId(userId);
        attempt.setReviewCardId(cardId);
        attempt.setReviewCycleId(cycleId);
        attempt.setReviewCycleQuestionId(cycleQuestionId);
        attempt.setUserAnswerId(userAnswerId);
        attempt.setAttemptSource(source);
        attempt.setResult(result);
        attempt.setSm2Quality(quality);
        attempt.setTargetErrorResolved(resolved);
        attempt.setAiFeedback(feedback);
        attempt.setEaseFactorAfter(sm2.easeFactor());
        attempt.setRepetitionCountAfter(sm2.repetitionCount());
        attempt.setIntervalDaysAfter(sm2.intervalDays());
        attempt.setCycleSuccessCountAfter(cycleSuccessCount);
        attempt.setNextDueAt(nextDueAt);
        attempt.setCreatedAt(now);
        return attempt;
    }

    private Question newDerivedQuestion(
            Question base,
            AiReviewGeneratedQuestionDTO generated,
            LocalDateTime now
    ) {
        Question question = new Question();
        question.setQuestionType(base.getQuestionType());
        question.setSourceText(generated.sourceText().trim());
        question.setContextText(generated.contextText().trim());
        question.setLevel(base.getLevel());
        question.setDifficulty(base.getDifficulty());
        question.setGrammarPoint(generated.grammarPoint().trim());
        question.setSpoken(base.getSpoken());
        question.setBusiness(base.getBusiness());
        question.setExam(base.getExam());
        question.setSourceType(SOURCE_REVIEW_DERIVED);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        return question;
    }

    private QuestionAnswer newDerivedAnswer(Long questionId, AiQuestionAnswerDTO dto, LocalDateTime now) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(questionId);
        answer.setAnswerText(dto.answerText().trim());
        answer.setAnswerType(dto.answerType());
        answer.setPrimaryAnswer(dto.primaryAnswer());
        answer.setSortOrder(dto.sortOrder());
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        return answer;
    }

    private List<AnswerErrorAnalysisVO> toErrorAnalysisVO(
            List<AiAnswerErrorAnalysisDTO> errors,
            Map<String, AiErrorTypeOptionDTO> optionsByCode
    ) {
        return errors.stream().map(error -> {
            AiErrorTypeOptionDTO option = optionsByCode.get(error.errorTypeCode());
            return new AnswerErrorAnalysisVO(
                    "CANDIDATE", option.id(), option.code(), option.name(), error.original().trim(),
                    error.issue().trim(), error.suggestion().trim(), error.severity(),
                    error.suggestedUserErrorTypeName().trim(), error.suggestedUserErrorTypeDescription().trim());
        }).toList();
    }

    private QuestionAnswerVO toAnswerVO(QuestionAnswer answer) {
        return new QuestionAnswerVO(answer.getId(), answer.getAnswerText(), answer.getAnswerType(),
                answer.getPrimaryAnswer(), answer.getSortOrder());
    }

    private ReviewAttemptHistoryVO toReviewAttemptHistoryVO(ReviewAttemptHistoryRow row) {
        return new ReviewAttemptHistoryVO(
                row.getId(), row.getCycleNo(), row.getQuestionRole(), row.getSourceText(), row.getReferenceAnswer(),
                row.getAnswerText(), row.getResult(), row.getTotalScore(), row.getQuality(), row.getCreatedAt()
        );
    }

    private TagVO toTagVO(Tag tag) {
        return new TagVO(tag.getId(), tag.getTagType(), tag.getParentId(), tag.getCode(), tag.getName(),
                tag.getDescription(), tag.getNameEn(), tag.getDescriptionEn(), tag.getSortOrder());
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    private record GeneratedQuestionIds(Long questionId, Long cycleQuestionId) {
    }
}
