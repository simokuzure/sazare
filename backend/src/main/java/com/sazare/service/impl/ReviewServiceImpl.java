package com.sazare.service.impl;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiAnswerErrorAnalysisDTO;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.AiReviewDTO;
import com.sazare.dto.ReviewAttemptHistoryRow;
import com.sazare.dto.ReviewAttemptRequest;
import com.sazare.dto.ReviewCardListRow;
import com.sazare.dto.ReviewCardQueryRequest;
import com.sazare.dto.ReviewCycleProgressRow;
import com.sazare.entity.ErrorType;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.ReviewAttempt;
import com.sazare.entity.ReviewCard;
import com.sazare.entity.ReviewCycle;
import com.sazare.entity.ReviewCycleQuestion;
import com.sazare.entity.Tag;
import com.sazare.entity.User;
import com.sazare.entity.UserAnswer;
import com.sazare.entity.UserErrorType;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.mapper.QuestionAnswerMapper;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.ReviewAttemptMapper;
import com.sazare.mapper.ReviewCardMapper;
import com.sazare.mapper.ReviewCycleMapper;
import com.sazare.mapper.ReviewCycleQuestionMapper;
import com.sazare.mapper.TagMapper;
import com.sazare.mapper.UserAnswerMapper;
import com.sazare.mapper.UserErrorTypeMapper;
import com.sazare.mapper.UserMapper;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewScoringClient;
import com.sazare.service.ai.prompt.AiReviewScoringPromptBuilder;
import com.sazare.service.ai.validation.ReviewAiResponseValidator;
import com.sazare.service.ReviewService;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.review.Sm2Result;
import com.sazare.service.review.ReviewDerivedQuestionService;
import com.sazare.service.review.Sm2Scheduler;
import com.sazare.util.ReviewDueAtCalculator;
import com.sazare.vo.AnswerErrorAnalysisVO;
import com.sazare.vo.AnswerScoresVO;
import com.sazare.vo.PageVO;
import com.sazare.vo.QuestionAnswerVO;
import com.sazare.vo.ReviewAttemptHistoryVO;
import com.sazare.vo.ReviewAttemptVO;
import com.sazare.vo.ReviewCardDetailVO;
import com.sazare.vo.ReviewCardListVO;
import com.sazare.vo.ReviewCycleProgressVO;
import com.sazare.vo.ReviewDerivedQuestionGenerationVO;
import com.sazare.vo.ReviewQuestionVO;
import com.sazare.vo.TagVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sazare.service.review.ReviewDerivedQuestionService.GeneratedQuestionIds;
import static com.sazare.service.review.ReviewDerivedQuestionService.PreparedDerivedQuestion;

@Service
public class ReviewServiceImpl implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewServiceImpl.class);
    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String CARD_ACTIVE = "ACTIVE";
    private static final String CARD_MASTERED = "MASTERED";
    private static final String CYCLE_IN_PROGRESS = "IN_PROGRESS";
    private static final String QUESTION_ORIGINAL = "ORIGINAL";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RETRY = "RETRY";
    private static final String STATUS_PASSED = "PASSED";
    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";

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
    private final TagMapper tagMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final Sm2Scheduler sm2Scheduler;
    private final AiReviewScoringPromptBuilder scoringPromptBuilder;
    private final AiReviewScoringClient scoringClient;
    private final ReviewAiResponseValidator aiResponseValidator;
    private final ReviewDerivedQuestionService derivedQuestionService;
    private final TransactionTemplate transactionTemplate;

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
            TagMapper tagMapper,
            UserAnswerMapper userAnswerMapper,
            Sm2Scheduler sm2Scheduler,
            AiReviewScoringPromptBuilder scoringPromptBuilder,
            AiReviewScoringClient scoringClient,
            ReviewAiResponseValidator aiResponseValidator,
            ReviewDerivedQuestionService derivedQuestionService,
            TransactionTemplate transactionTemplate
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
        this.tagMapper = tagMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.sm2Scheduler = sm2Scheduler;
        this.scoringPromptBuilder = scoringPromptBuilder;
        this.scoringClient = scoringClient;
        this.aiResponseValidator = aiResponseValidator;
        this.derivedQuestionService = derivedQuestionService;
        this.transactionTemplate = transactionTemplate;
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
    public ReviewAttemptVO submitReviewAttempt(Long cardId, ReviewAttemptRequest request) {
        ReviewAttemptContext context = loadReviewAttemptContext(cardId, request);

        AiReviewDTO review;
        try {
            AiQuestionPrompt prompt = scoringPromptBuilder.build(
                    context.userErrorType(), context.errorType(), context.question(), context.standardAnswers(),
                    context.errorTypeOptions(), request.answerText());
            review = aiResponseValidator.parseScoring(
                    scoringClient.scoreAnswer(prompt), context.errorTypesByCode(), request.answerText().trim());
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "复习 AI 评分失败");
        }

        ReviewAttemptWriteResult writeResult = transactionTemplate.execute(
                status -> saveReviewAttempt(context, request, review));
        ReviewCycleProgressRow progress = writeResult.progress();
        String generationStatus = "NOT_REQUIRED";
        if (writeResult.requiresDerivedQuestion()) {
            try {
                PreparedDerivedQuestion prepared = derivedQuestionService.prepare(
                        writeResult.cycle(), context.userErrorType(), context.errorType());
                DerivedQuestionWriteResult generated = transactionTemplate.execute(status -> savePreparedDerivedQuestion(
                        writeResult.card().getId(), context.user().getId(), writeResult.cycle().getId(), prepared));
                generationStatus = "SUCCEEDED";
                progress = generated.progress();
            } catch (BusinessException exception) {
                log.error(
                        "复习衍生题生成失败: cardId={}, cycleId={}",
                        writeResult.card().getId(),
                        writeResult.cycle().getId(),
                        exception
                );
                generationStatus = "FAILED";
            }
        }

        return new ReviewAttemptVO(
                writeResult.userAnswer().getId(), review.quality(), writeResult.passed() ? RESULT_PASS : RESULT_FAIL,
                review.targetErrorResolved(), review.feedback(), new AnswerScoresVO(
                        review.scores().grammarVocabularyScore(),
                        review.scores().naturalFluencyScore(),
                        review.scores().scenarioAdaptationScore(),
                        review.scores().informationCompletenessScore()
                ), writeResult.totalScore(),
                toErrorAnalysisVO(review.errorAnalysis(), context.errorTypesByCode()),
                toProgressVO(writeResult.cycle(), progress), writeResult.nextDueAt(),
                writeResult.completed() ? CARD_MASTERED : CARD_ACTIVE,
                context.standardAnswers().stream().map(this::toAnswerVO).toList(), generationStatus
        );
    }

    private ReviewAttemptContext loadReviewAttemptContext(Long cardId, ReviewAttemptRequest request) {
        User user = requireLocalUser();
        ReviewCard card = requireCard(reviewCardMapper.selectByIdAndUserId(cardId, user.getId()));
        requireReadyCard(card, LocalDateTime.now(), request.earlyReview());
        ReviewCycle cycle = reviewCycleMapper.selectCurrentByCardId(card.getId());
        if (cycle == null) {
            throw business("复习卡片没有进行中的周期");
        }
        ReviewCycleQuestion cycleQuestion = reviewCycleQuestionMapper
                .selectByIdAndCycleId(request.cycleQuestionId(), cycle.getId());
        validateAttemptEligibility(cycle, cycleQuestion);
        Question question = requireQuestion(cycleQuestion.getQuestionId());
        List<QuestionAnswer> standardAnswers = requireAnswers(question.getId());
        UserErrorType userErrorType = requireUserErrorType(card, user.getId());
        TranslationDirection direction = TranslationDirection.fromLearningMode(userErrorType.getLearningMode());
        ErrorType errorType = requireErrorType(userErrorType.getErrorTypeId());
        List<AiErrorTypeOptionDTO> errorTypeOptions = localizeErrorTypes(
                dictionaryCacheService.getEnabledLeafErrorTypes(), direction);
        Map<String, AiErrorTypeOptionDTO> errorTypesByCode = errorTypeOptions.stream()
                .collect(Collectors.toMap(AiErrorTypeOptionDTO::code, Function.identity()));
        return new ReviewAttemptContext(
                user, card, cycle, cycleQuestion, question, standardAnswers, userErrorType, errorType,
                errorTypeOptions, errorTypesByCode);
    }

    private ReviewAttemptWriteResult saveReviewAttempt(
            ReviewAttemptContext context,
            ReviewAttemptRequest request,
            AiReviewDTO review
    ) {
        LocalDateTime now = LocalDateTime.now();
        ReviewCard card = context.card();
        ReviewCycle cycle = context.cycle();
        ReviewCycleQuestion cycleQuestion = context.cycleQuestion();

        BigDecimal totalScore = calculateTotalScore(review);
        UserAnswer userAnswer = createSubmittedAnswer(
                context.user().getId(), context.question().getId(), context.userErrorType().getLearningMode(),
                request.answerText().trim(), now);
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
                context.user().getId(), card.getId(), cycle.getId(), cycleQuestion.getId(), userAnswer.getId(),
                "REVIEW", passed ? RESULT_PASS : RESULT_FAIL, review.quality(), passed, review.feedback(),
                sm2, successfulCount, nextDueAt, now);
        reviewAttemptMapper.insertAttempt(attempt);
        return new ReviewAttemptWriteResult(
                card, cycle, userAnswer, progress, nextDueAt, totalScore, passed, completed,
                !completed && requiresDerivedQuestion(cycle, progress));
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
    public ReviewDerivedQuestionGenerationVO generateDerivedQuestion(Long cardId) {
        DerivedGenerationContext context = transactionTemplate.execute(
                status -> loadDerivedGenerationContext(cardId));
        PreparedDerivedQuestion prepared = derivedQuestionService.prepare(
                context.cycle(), context.userErrorType(), context.errorType());
        DerivedQuestionWriteResult result = transactionTemplate.execute(status -> savePreparedDerivedQuestion(
                context.card().getId(), context.user().getId(), context.cycle().getId(), prepared));
        return new ReviewDerivedQuestionGenerationVO(
                result.ids().questionId(), result.ids().cycleQuestionId(), "SUCCEEDED");
    }

    private DerivedGenerationContext loadDerivedGenerationContext(Long cardId) {
        User user = requireLocalUser();
        ReviewCard card = requireCard(reviewCardMapper.selectForUpdateByIdAndUserId(cardId, user.getId()));
        if (!CARD_ACTIVE.equals(card.getStatus())) {
            throw business("已掌握卡片不能生成衍生题");
        }
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        UserErrorType userErrorType = requireUserErrorType(card, user.getId());
        ErrorType errorType = requireErrorType(userErrorType.getErrorTypeId());
        if (!requiresDerivedQuestion(cycle, loadProgress(cycle))) {
            throw business("当前周期不需要生成衍生题");
        }
        return new DerivedGenerationContext(user, card, cycle, userErrorType, errorType);
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
        ReviewCard card = recordPracticeError(
                userId,
                userAnswerId,
                distinctQuestionIds.getFirst(),
                userErrorTypeId,
                occurredAt
        );
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        int insertedCount = 0;
        for (Long questionId : distinctQuestionIds) {
            ReviewCycleQuestion cycleQuestion = newOriginalRetryQuestion(cycle.getId(), questionId, occurredAt);
            insertedCount += reviewCycleQuestionMapper.insertQuestionIfAbsent(cycleQuestion);
        }
        if (insertedCount == 0) {
            return;
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

    private DerivedQuestionWriteResult savePreparedDerivedQuestion(
            Long cardId,
            Long userId,
            Long expectedCycleId,
            PreparedDerivedQuestion prepared
    ) {
        ReviewCard card = requireCard(reviewCardMapper.selectForUpdateByIdAndUserId(cardId, userId));
        if (!CARD_ACTIVE.equals(card.getStatus())) {
            throw business("已掌握卡片不能生成衍生题");
        }
        ReviewCycle cycle = requireCurrentCycle(card.getId());
        if (!cycle.getId().equals(expectedCycleId) || !requiresDerivedQuestion(cycle, loadProgress(cycle))) {
            throw business("复习周期已变化，当前不需要生成衍生题");
        }

        LocalDateTime now = LocalDateTime.now();
        GeneratedQuestionIds ids = derivedQuestionService.save(cycle, prepared, now);
        return new DerivedQuestionWriteResult(ids, loadProgress(cycle));
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

    private void validateAttemptEligibility(
            ReviewCycle cycle,
            ReviewCycleQuestion requested
    ) {
        if (requested == null || STATUS_PASSED.equals(requested.getReviewStatus())) {
            throw business("复习题不存在或当前不可作答");
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

    private record ReviewAttemptContext(
            User user,
            ReviewCard card,
            ReviewCycle cycle,
            ReviewCycleQuestion cycleQuestion,
            Question question,
            List<QuestionAnswer> standardAnswers,
            UserErrorType userErrorType,
            ErrorType errorType,
            List<AiErrorTypeOptionDTO> errorTypeOptions,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
    }

    private record ReviewAttemptWriteResult(
            ReviewCard card,
            ReviewCycle cycle,
            UserAnswer userAnswer,
            ReviewCycleProgressRow progress,
            LocalDateTime nextDueAt,
            BigDecimal totalScore,
            boolean passed,
            boolean completed,
            boolean requiresDerivedQuestion
    ) {
    }

    private record DerivedGenerationContext(
            User user,
            ReviewCard card,
            ReviewCycle cycle,
            UserErrorType userErrorType,
            ErrorType errorType
    ) {
    }

    private record DerivedQuestionWriteResult(
            GeneratedQuestionIds ids,
            ReviewCycleProgressRow progress
    ) {
    }
}
