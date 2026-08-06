package com.jt.learning.service.impl;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.ReviewAttemptRequest;
import com.jt.learning.dto.ReviewCycleProgressRow;
import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.ReviewCard;
import com.jt.learning.entity.ReviewCycle;
import com.jt.learning.entity.ReviewCycleQuestion;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.entity.UserErrorType;
import com.jt.learning.exception.BusinessException;
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
import com.jt.learning.service.AiReviewQuestionClient;
import com.jt.learning.service.AiReviewScoringClient;
import com.jt.learning.service.Sm2Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceImplTest {

    private ReviewCardMapper cardMapper;
    private ReviewCycleMapper cycleMapper;
    private ReviewCycleQuestionMapper cycleQuestionMapper;
    private ReviewAttemptMapper attemptMapper;
    private UserMapper userMapper;
    private UserErrorTypeMapper userErrorTypeMapper;
    private ErrorTypeMapper errorTypeMapper;
    private QuestionMapper questionMapper;
    private QuestionAnswerMapper answerMapper;
    private QuestionTagMapper questionTagMapper;
    private TagMapper tagMapper;
    private UserAnswerMapper userAnswerMapper;
    private AiReviewScoringClient scoringClient;
    private AiReviewQuestionClient questionClient;
    private ReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        cardMapper = mock(ReviewCardMapper.class);
        cycleMapper = mock(ReviewCycleMapper.class);
        cycleQuestionMapper = mock(ReviewCycleQuestionMapper.class);
        attemptMapper = mock(ReviewAttemptMapper.class);
        userMapper = mock(UserMapper.class);
        userErrorTypeMapper = mock(UserErrorTypeMapper.class);
        errorTypeMapper = mock(ErrorTypeMapper.class);
        questionMapper = mock(QuestionMapper.class);
        answerMapper = mock(QuestionAnswerMapper.class);
        questionTagMapper = mock(QuestionTagMapper.class);
        tagMapper = mock(TagMapper.class);
        userAnswerMapper = mock(UserAnswerMapper.class);
        scoringClient = mock(AiReviewScoringClient.class);
        questionClient = mock(AiReviewQuestionClient.class);

        ObjectMapper objectMapper = new ObjectMapper();
        AiErrorAnalysisValidator errorValidator = new AiErrorAnalysisValidator();
        service = new ReviewServiceImpl(
                cardMapper, cycleMapper, cycleQuestionMapper, attemptMapper, userMapper,
                userErrorTypeMapper, errorTypeMapper, questionMapper, answerMapper, questionTagMapper,
                tagMapper, userAnswerMapper, new Sm2Scheduler(),
                new AiReviewScoringPromptBuilder(objectMapper),
                new AiReviewQuestionPromptBuilder(objectMapper),
                scoringClient, questionClient,
                new ReviewAiResponseValidator(objectMapper, errorValidator)
        );
    }

    @Test
    void submitShouldRejectCardBeforeDue() {
        stubLocalUser();
        ReviewCard card = card("ACTIVE", LocalDateTime.now().plusHours(1));
        when(cardMapper.selectForUpdateByIdAndUserId(1L, 1L)).thenReturn(card);

        assertThatThrownBy(() -> service.submitReviewAttempt(1L, new ReviewAttemptRequest(30L, 0, "答案")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未到期");

        verify(cycleMapper, never()).selectCurrentForUpdateByCardId(any());
    }

    @Test
    void submitShouldRejectExpiredAttemptVersion() {
        stubLocalUser();
        ReviewCard card = card("ACTIVE", LocalDateTime.now().minusMinutes(1));
        ReviewCycle cycle = cycle(0, 4);
        ReviewCycleQuestion question = cycleQuestion("RETRY", "ORIGINAL", 2);
        when(cardMapper.selectForUpdateByIdAndUserId(1L, 1L)).thenReturn(card);
        when(cycleMapper.selectCurrentForUpdateByCardId(1L)).thenReturn(cycle);
        when(cycleQuestionMapper.selectByIdAndCycleId(30L, 20L)).thenReturn(question);

        assertThatThrownBy(() -> service.submitReviewAttempt(1L, new ReviewAttemptRequest(30L, 1, "答案")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本已过期");
    }

    @Test
    void submitShouldRequireLatestRetryQuestion() {
        stubLocalUser();
        ReviewCard card = card("ACTIVE", LocalDateTime.now().minusMinutes(1));
        ReviewCycle cycle = cycle(0, 4);
        ReviewCycleQuestion requested = cycleQuestion("PENDING", "ORIGINAL", 0);
        ReviewCycleQuestion retry = cycleQuestion("RETRY", "ORIGINAL", 1);
        retry.setId(31L);
        when(cardMapper.selectForUpdateByIdAndUserId(1L, 1L)).thenReturn(card);
        when(cycleMapper.selectCurrentForUpdateByCardId(1L)).thenReturn(cycle);
        when(cycleQuestionMapper.selectByIdAndCycleId(30L, 20L)).thenReturn(requested);
        when(cycleQuestionMapper.selectLatestRetry(20L)).thenReturn(retry);

        assertThatThrownBy(() -> service.submitReviewAttempt(1L, new ReviewAttemptRequest(30L, 0, "答案")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最近答错");
    }

    @Test
    void scoringFailureShouldOnlyMarkAnswerFailed() {
        stubReadyAttempt("RETRY", "ORIGINAL", 0, 4);
        when(scoringClient.scoreAnswer(any())).thenThrow(new BusinessException(
                com.jt.learning.exception.ErrorCode.BUSINESS_ERROR, "评分失败"));

        assertThatThrownBy(() -> service.submitReviewAttempt(
                1L, new ReviewAttemptRequest(30L, 0, "電車に間に合いました")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评分失败");

        verify(userAnswerMapper).updateFailed(eq(40L), any());
        verify(userAnswerMapper, never()).updateReviewed(
                anyLong(), anyInt(), anyInt(), anyInt(), anyInt(),
                any(BigDecimal.class), anyString(), any(LocalDateTime.class));
        verify(cycleQuestionMapper, never()).markAttempt(
                anyLong(), anyString(), anyInt(), anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(attemptMapper, never()).insertAttempt(any());
    }

    @Test
    void failedReviewShouldResetQuestionAndScheduleNextDay() {
        ReviewCard card = stubReadyAttempt("RETRY", "ORIGINAL", 0, 4);
        when(scoringClient.scoreAnswer(any())).thenReturn("""
                {"review":{"quality":2,"targetErrorResolved":false,"feedback":"目标错误仍存在。","scores":{"grammarVocabularyScore":80,"naturalFluencyScore":70,"scenarioAdaptationScore":90,"informationCompletenessScore":81},"errorAnalysis":[]}}
                """);
        when(cycleQuestionMapper.selectProgress(eq(20L), any())).thenReturn(progress(1, 0, 1, 0, 1, 0, 0));

        var result = service.submitReviewAttempt(1L, new ReviewAttemptRequest(30L, 0, "電車を間に合いました"));

        assertThat(result.result()).isEqualTo("FAIL");
        assertThat(result.totalScore()).isEqualByComparingTo("80.25");
        assertThat(result.scores().informationCompletenessScore()).isEqualTo(81);
        assertThat(result.nextDueAt()).isAfter(LocalDateTime.now().plusHours(23));
        verify(userAnswerMapper).updateReviewed(
                eq(40L), eq(80), eq(70), eq(90), eq(81), eq(new BigDecimal("80.25")),
                eq("目标错误仍存在。"), any());
        verify(cycleQuestionMapper).markAttempt(eq(30L), eq("RETRY"), eq(1), eq(2), any(), eq(null), any());
        ArgumentCaptor<BigDecimal> easeCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(cardMapper).updateSchedule(
                eq(1L), eq("ACTIVE"), easeCaptor.capture(), eq(0), eq(1), eq(1), any(), any(), eq(null), any());
        assertThat(easeCaptor.getValue()).isEqualByComparingTo("2.1800");
    }

    @Test
    void passingFinalDerivedQuestionShouldCompleteCycleAndMasterCard() {
        stubReadyAttempt("RETRY", "DERIVED", 3, 4);
        when(scoringClient.scoreAnswer(any())).thenReturn("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":88,"naturalFluencyScore":86,"scenarioAdaptationScore":90,"informationCompletenessScore":92},"errorAnalysis":[]}}
                """);
        when(cycleQuestionMapper.selectProgress(eq(20L), any())).thenReturn(progress(1, 1, 0, 0, 0, 1, 1));

        var result = service.submitReviewAttempt(1L, new ReviewAttemptRequest(30L, 0, "電車に間に合いました"));

        assertThat(result.cardStatus()).isEqualTo("MASTERED");
        assertThat(result.nextDueAt()).isNull();
        verify(cycleMapper).completeCycle(eq(20L), any());
        verify(cardMapper).updateSchedule(
                eq(1L), eq("MASTERED"), any(), eq(1), eq(1), eq(0), eq(null), any(), any(), any());
    }

    @Test
    void generationFailureAfterPassShouldKeepAttemptAndReturnFailedStatus() {
        stubReadyAttempt("RETRY", "ORIGINAL", 0, 4);
        when(scoringClient.scoreAnswer(any())).thenReturn("""
                {"review":{"quality":4,"targetErrorResolved":true,"feedback":"目标错误已解决。","scores":{"grammarVocabularyScore":88,"naturalFluencyScore":86,"scenarioAdaptationScore":90,"informationCompletenessScore":92},"errorAnalysis":[]}}
                """);
        ReviewCycleProgressRow progress = progress(1, 1, 0, 0, 0, 0, 0);
        when(cycleQuestionMapper.selectProgress(eq(20L), any())).thenReturn(progress);
        when(cycleQuestionMapper.selectAllByCycleId(20L)).thenReturn(List.of(cycleQuestion("PASSED", "ORIGINAL", 1)));
        when(questionMapper.selectQuestionsByIds(any())).thenReturn(List.of(question()));
        when(answerMapper.selectActiveAnswersByQuestionIds(any())).thenReturn(List.of(answer()));
        when(questionClient.generateQuestion(any())).thenThrow(new BusinessException(
                com.jt.learning.exception.ErrorCode.BUSINESS_ERROR, "生成失败"));

        var result = service.submitReviewAttempt(1L, new ReviewAttemptRequest(30L, 0, "電車に間に合いました"));

        assertThat(result.derivedGenerationStatus()).isEqualTo("FAILED");
        verify(attemptMapper).insertAttempt(any());
        verify(userAnswerMapper).updateReviewed(
                eq(40L), eq(88), eq(86), eq(90), eq(92), eq(new BigDecimal("89.00")),
                eq("目标错误已解决。"), any());
    }

    @Test
    void masteredPracticeFailureShouldCreateNextCycle() {
        ReviewCard card = card("MASTERED", null);
        card.setMasteredAt(LocalDateTime.now().minusDays(1));
        when(userErrorTypeMapper.selectActiveByIdAndUserId(2L, 1L)).thenReturn(userErrorType());
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question());
        when(cardMapper.selectForUpdateByUserErrorTypeId(2L)).thenReturn(card);
        when(attemptMapper.existsByCardIdAndUserAnswerId(1L, 50L)).thenReturn(false);
        when(cycleMapper.selectMaxCycleNo(1L)).thenReturn(1);

        service.recordPracticeError(1L, 50L, 100L, 2L, LocalDateTime.now());

        ArgumentCaptor<ReviewCycle> cycleCaptor = ArgumentCaptor.forClass(ReviewCycle.class);
        verify(cycleMapper).insertCycle(cycleCaptor.capture());
        assertThat(cycleCaptor.getValue().getCycleNo()).isEqualTo(2);
        verify(cardMapper).updateSchedule(
                eq(1L), eq("ACTIVE"), any(), eq(0), eq(1), eq(1), any(), any(), eq(null), any());
    }

    @Test
    void firstPracticeFailureShouldCreateCardCycleAndSingleLapse() {
        when(userErrorTypeMapper.selectActiveByIdAndUserId(2L, 1L)).thenReturn(userErrorType());
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question());
        AtomicReference<ReviewCard> insertedCard = new AtomicReference<>();
        when(cardMapper.selectForUpdateByUserErrorTypeId(2L))
                .thenReturn(null)
                .thenAnswer(invocation -> insertedCard.get());
        when(cardMapper.insertCardIfAbsent(any())).thenAnswer(invocation -> {
            ReviewCard card = invocation.getArgument(0);
            card.setId(1L);
            insertedCard.set(card);
            return 1;
        });
        when(cycleMapper.insertCycle(any())).thenAnswer(invocation -> {
            ReviewCycle cycle = invocation.getArgument(0);
            cycle.setId(20L);
            return 1;
        });
        when(cycleQuestionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            ReviewCycleQuestion question = invocation.getArgument(0);
            question.setId(30L);
            return 1;
        });

        service.recordPracticeError(1L, 50L, 100L, 2L, LocalDateTime.now());

        verify(cardMapper).updateSchedule(
                eq(1L), eq("ACTIVE"), eq(new BigDecimal("2.1800")), eq(0), eq(1), eq(1),
                any(), any(), eq(null), any());
        verify(attemptMapper).insertAttempt(any());
    }

    @Test
    void explicitGenerationRetryShouldPersistDerivedQuestion() {
        stubLocalUser();
        ReviewCard card = card("ACTIVE", LocalDateTime.now().plusDays(1));
        ReviewCycle cycle = cycle(1, 4);
        ReviewCycleQuestion original = cycleQuestion("PASSED", "ORIGINAL", 1);
        when(cardMapper.selectForUpdateByIdAndUserId(1L, 1L)).thenReturn(card);
        when(cycleMapper.selectCurrentForUpdateByCardId(1L)).thenReturn(cycle);
        when(userErrorTypeMapper.selectActiveByIdAndUserId(2L, 1L)).thenReturn(userErrorType());
        when(errorTypeMapper.selectEnabledLeafById(3L)).thenReturn(errorType());
        when(cycleQuestionMapper.selectProgress(eq(20L), any()))
                .thenReturn(progress(1, 1, 0, 0, 0, 0, 0));
        when(cycleQuestionMapper.selectAllByCycleId(20L)).thenReturn(List.of(original));
        when(questionMapper.selectQuestionsByIds(List.of(100L))).thenReturn(List.of(question()));
        when(answerMapper.selectActiveAnswersByQuestionIds(List.of(100L))).thenReturn(List.of(answer()));
        when(questionClient.generateQuestion(any())).thenReturn("""
                {"question":{"sourceText":"我准时赶上了公交车。","contextText":"日常出行","grammarPoint":"に間に合う","answers":[{"answerText":"バスに間に合いました。","answerType":"STANDARD","primaryAnswer":true,"sortOrder":0}]}}
                """);
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question derived = invocation.getArgument(0);
            derived.setId(101L);
            return 1;
        });
        when(cycleQuestionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            ReviewCycleQuestion derived = invocation.getArgument(0);
            derived.setId(31L);
            return 1;
        });

        var result = service.generateDerivedQuestion(1L);

        assertThat(result.questionId()).isEqualTo(101L);
        assertThat(result.cycleQuestionId()).isEqualTo(31L);
        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).insertQuestion(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getSourceType()).isEqualTo("REVIEW_DERIVED");
        verify(answerMapper).insertQuestionAnswer(any());
    }

    private ReviewCard stubReadyAttempt(String status, String role, int successfulCount, int targetCount) {
        stubLocalUser();
        ReviewCard card = card("ACTIVE", LocalDateTime.now().minusMinutes(1));
        ReviewCycle cycle = cycle(successfulCount, targetCount);
        ReviewCycleQuestion cycleQuestion = cycleQuestion(status, role, 0);
        when(cardMapper.selectForUpdateByIdAndUserId(1L, 1L)).thenReturn(card);
        when(cycleMapper.selectCurrentForUpdateByCardId(1L)).thenReturn(cycle);
        when(cycleQuestionMapper.selectByIdAndCycleId(30L, 20L)).thenReturn(cycleQuestion);
        when(cycleQuestionMapper.selectLatestRetry(20L))
                .thenReturn("RETRY".equals(status) ? cycleQuestion : null);
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question());
        when(answerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer()));
        when(userErrorTypeMapper.selectActiveByIdAndUserId(2L, 1L)).thenReturn(userErrorType());
        when(errorTypeMapper.selectEnabledLeafById(3L)).thenReturn(errorType());
        when(errorTypeMapper.selectEnabledLeafOptions()).thenReturn(List.of(errorTypeOption()));
        when(userAnswerMapper.insertUserAnswer(any())).thenAnswer(invocation -> {
            UserAnswer userAnswer = invocation.getArgument(0);
            userAnswer.setId(40L);
            return 1;
        });
        return card;
    }

    private void stubLocalUser() {
        User user = new User();
        user.setId(1L);
        user.setUserCode("LOCAL_DEFAULT");
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
    }

    private ReviewCard card(String status, LocalDateTime dueAt) {
        ReviewCard card = new ReviewCard();
        card.setId(1L);
        card.setUserId(1L);
        card.setUserErrorTypeId(2L);
        card.setStatus(status);
        card.setEaseFactor(new BigDecimal("2.5000"));
        card.setRepetitionCount(0);
        card.setIntervalDays(1);
        card.setLapseCount(0);
        card.setDueAt(dueAt);
        return card;
    }

    private ReviewCycle cycle(int successfulCount, int targetCount) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(20L);
        cycle.setReviewCardId(1L);
        cycle.setCycleNo(1);
        cycle.setStatus("IN_PROGRESS");
        cycle.setSuccessfulReviewCount(successfulCount);
        cycle.setTargetSuccessCount(targetCount);
        cycle.setVerificationRequiredAfter(LocalDateTime.now().minusDays(1));
        return cycle;
    }

    private ReviewCycleQuestion cycleQuestion(String status, String role, int attemptCount) {
        ReviewCycleQuestion question = new ReviewCycleQuestion();
        question.setId(30L);
        question.setReviewCycleId(20L);
        question.setQuestionId(100L);
        question.setQuestionRole(role);
        question.setReviewStatus(status);
        question.setAttemptCount(attemptCount);
        question.setSortOrder(0);
        question.setLastAttemptAt(LocalDateTime.now().minusDays(1));
        return question;
    }

    private ReviewCycleProgressRow progress(
            int originals,
            int originalsPassed,
            int retries,
            int pending,
            int active,
            int derived,
            int verifiedDerived
    ) {
        ReviewCycleProgressRow row = new ReviewCycleProgressRow();
        row.setOriginalQuestionCount(originals);
        row.setOriginalPassedCount(originalsPassed);
        row.setRetryQuestionCount(retries);
        row.setPendingQuestionCount(pending);
        row.setActiveQuestionCount(active);
        row.setDerivedQuestionCount(derived);
        row.setVerifiedDerivedPassedCount(verifiedDerived);
        return row;
    }

    private Question question() {
        Question question = new Question();
        question.setId(100L);
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("我赶上了电车。");
        question.setContextText("日常交流");
        question.setLevel("N4");
        question.setDifficulty(2);
        question.setGrammarPoint("に間に合う");
        question.setSpoken(true);
        question.setBusiness(false);
        question.setExam(false);
        question.setSourceType("MANUAL");
        question.setEnabled(true);
        question.setDeleted(false);
        return question;
    }

    private QuestionAnswer answer() {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(10L);
        answer.setQuestionId(100L);
        answer.setAnswerText("電車に間に合いました。");
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        return answer;
    }

    private UserErrorType userErrorType() {
        UserErrorType type = new UserErrorType();
        type.setId(2L);
        type.setUserId(1L);
        type.setErrorTypeId(3L);
        type.setName("赶上交通工具时误用を");
        type.setDescription("表示赶上交通工具时应使用に。");
        type.setStatus("ACTIVE");
        return type;
    }

    private ErrorType errorType() {
        ErrorType type = new ErrorType();
        type.setId(3L);
        type.setCode("PARTICLE_CASE");
        type.setName("格助词");
        return type;
    }

    private AiErrorTypeOptionDTO errorTypeOption() {
        return new AiErrorTypeOptionDTO(3L, "PARTICLE_CASE", "格助词", "说明", "PARTICLE", "助词");
    }
}
