package com.sazare.service;

import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.entity.Question;
import com.sazare.entity.Tag;
import com.sazare.event.ReviewQuestionTagEnrichmentRequestedEvent;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import com.sazare.service.ai.AiReviewQuestionClient;
import com.sazare.service.ai.client.AiProviderHttpException;
import com.sazare.service.ai.prompt.AiReviewTagPromptBuilder;
import com.sazare.service.ai.validation.AiErrorAnalysisValidator;
import com.sazare.service.ai.validation.ReviewAiResponseValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewQuestionTagEnrichmentServiceTest {

    private TaskExecutor taskExecutor;
    private TaskScheduler taskScheduler;
    private QuestionMapper questionMapper;
    private QuestionTagMapper questionTagMapper;
    private DictionaryCacheService dictionaryCacheService;
    private AiReviewQuestionClient questionClient;
    private ReviewQuestionTagWriter tagWriter;
    private ReviewQuestionTagEnrichmentService service;

    @BeforeEach
    void setUp() {
        taskExecutor = mock(TaskExecutor.class);
        taskScheduler = mock(TaskScheduler.class);
        questionMapper = mock(QuestionMapper.class);
        questionTagMapper = mock(QuestionTagMapper.class);
        dictionaryCacheService = mock(DictionaryCacheService.class);
        questionClient = mock(AiReviewQuestionClient.class);
        tagWriter = mock(ReviewQuestionTagWriter.class);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new ReviewQuestionTagEnrichmentService(
                taskExecutor,
                taskScheduler,
                questionMapper,
                questionTagMapper,
                dictionaryCacheService,
                new AiReviewTagPromptBuilder(objectMapper),
                questionClient,
                new ReviewAiResponseValidator(objectMapper, new AiErrorAnalysisValidator()),
                tagWriter
        );
    }

    @Test
    void listenerShouldRunOnlyAfterCommit() throws NoSuchMethodException {
        Method listener = ReviewQuestionTagEnrichmentService.class.getMethod(
                "onTagEnrichmentRequested",
                ReviewQuestionTagEnrichmentRequestedEvent.class
        );

        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(annotation.fallbackExecution()).isFalse();
    }

    @Test
    void shouldClassifyAndSaveTagsInBackground() {
        runTasksImmediately();
        Question question = reviewQuestion(500L);
        Tag sceneTag = tag(10L, "SCENE", "BANK");
        Tag functionTag = tag(11L, "FUNCTION", "FUNCTION_REQUEST");
        Tag parentSceneTag = tag(12L, "SCENE", "FINANCE");
        parentSceneTag.setParentId(null);
        Tag parentFunctionTag = tag(13L, "FUNCTION", "FUNCTION_REQUEST_GROUP");
        parentFunctionTag.setParentId(null);
        when(questionMapper.selectQuestionById(500L)).thenReturn(question);
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(0);
        when(dictionaryCacheService.getEnabledTagsByType("SCENE")).thenReturn(List.of(parentSceneTag, sceneTag));
        when(dictionaryCacheService.getEnabledTagsByType("FUNCTION")).thenReturn(List.of(parentFunctionTag, functionTag));
        when(questionClient.classifyTags(any(), any(), any()))
                .thenReturn("{\"tagCodes\":[\"BANK\",\"FUNCTION_REQUEST\"]}");
        when(tagWriter.saveIfUntagged(500L, List.of(10L, 11L))).thenReturn(true);

        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        verify(questionClient).classifyTags(any(),
                argThat(options -> options.size() == 1 && "BANK".equals(options.getFirst().code())),
                argThat(options -> options.size() == 1 && "FUNCTION_REQUEST".equals(options.getFirst().code())));
        verify(tagWriter).saveIfUntagged(500L, List.of(10L, 11L));
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldSkipQuestionThatAlreadyHasTags() {
        runTasksImmediately();
        when(questionMapper.selectQuestionById(500L)).thenReturn(reviewQuestion(500L));
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(1);

        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        verify(questionClient, never()).classifyTags(any(), any(), any());
        verify(tagWriter, never()).saveIfUntagged(any(), any());
    }

    @Test
    void shouldIgnoreDuplicateEventWhileQuestionIsQueued() {
        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));
        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        verify(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    void shouldRetryRateLimitAfterOneAndThreeMinutesThenStop() {
        runTasksImmediately();
        when(questionMapper.selectQuestionById(500L)).thenReturn(reviewQuestion(500L));
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(0);
        when(dictionaryCacheService.getEnabledTagsByType("SCENE"))
                .thenReturn(List.of(tag(10L, "SCENE", "BANK")));
        when(dictionaryCacheService.getEnabledTagsByType("FUNCTION")).thenReturn(List.of());
        when(questionClient.classifyTags(any(), any(), any()))
                .thenThrow(new AiProviderHttpException(429, "rate limited"));

        Instant firstFailureAt = Instant.now();
        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        ArgumentCaptor<Runnable> retryTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Instant> retryAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(taskScheduler).schedule(retryTaskCaptor.capture(), retryAtCaptor.capture());
        assertDelay(retryAtCaptor.getValue(), firstFailureAt, Duration.ofMinutes(1));

        Instant secondFailureAt = Instant.now();
        retryTaskCaptor.getValue().run();

        verify(taskScheduler, times(2)).schedule(retryTaskCaptor.capture(), retryAtCaptor.capture());
        List<Runnable> retryTasks = retryTaskCaptor.getAllValues();
        List<Instant> retryTimes = retryAtCaptor.getAllValues();
        assertDelay(retryTimes.getLast(), secondFailureAt, Duration.ofMinutes(3));

        retryTasks.getLast().run();

        verify(questionClient, times(3)).classifyTags(any(), any(), any());
        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
        verify(tagWriter, never()).saveIfUntagged(any(), any());
    }

    @Test
    void shouldStopRetryingAfterSecondAttemptSucceeds() {
        runTasksImmediately();
        Tag sceneTag = tag(10L, "SCENE", "BANK");
        when(questionMapper.selectQuestionById(500L)).thenReturn(reviewQuestion(500L));
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(0);
        when(dictionaryCacheService.getEnabledTagsByType("SCENE")).thenReturn(List.of(sceneTag));
        when(dictionaryCacheService.getEnabledTagsByType("FUNCTION")).thenReturn(List.of());
        when(questionClient.classifyTags(any(), any(), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "temporary network failure",
                        new IOException("connection reset")
                ))
                .thenReturn("{\"tagCodes\":[\"BANK\"]}");
        when(tagWriter.saveIfUntagged(500L, List.of(10L))).thenReturn(true);

        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        ArgumentCaptor<Runnable> retryTaskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(retryTaskCaptor.capture(), any(Instant.class));
        retryTaskCaptor.getValue().run();

        verify(questionClient, times(2)).classifyTags(any(), any(), any());
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        verify(tagWriter).saveIfUntagged(500L, List.of(10L));
    }

    @Test
    void shouldNotRetryPermanentProviderFailure() {
        runTasksImmediately();
        when(questionMapper.selectQuestionById(500L)).thenReturn(reviewQuestion(500L));
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(0);
        when(dictionaryCacheService.getEnabledTagsByType("SCENE"))
                .thenReturn(List.of(tag(10L, "SCENE", "BANK")));
        when(dictionaryCacheService.getEnabledTagsByType("FUNCTION")).thenReturn(List.of());
        when(questionClient.classifyTags(any(), any(), any()))
                .thenThrow(new AiProviderHttpException(400, "invalid request"));

        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        verify(questionClient).classifyTags(any(), any(), any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void shouldNotCallAiWithoutSceneTagCandidates() {
        runTasksImmediately();
        when(questionMapper.selectQuestionById(500L)).thenReturn(reviewQuestion(500L));
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(0);
        when(dictionaryCacheService.getEnabledTagsByType("SCENE")).thenReturn(List.of());
        when(dictionaryCacheService.getEnabledTagsByType("FUNCTION")).thenReturn(List.of());

        service.onTagEnrichmentRequested(new ReviewQuestionTagEnrichmentRequestedEvent(500L));

        verify(questionClient, never()).classifyTags(any(), any(), any());
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    private void runTasksImmediately() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
    }

    private void assertDelay(Instant scheduledAt, Instant failureAt, Duration expectedDelay) {
        Duration actualDelay = Duration.between(failureAt, scheduledAt);
        assertThat(actualDelay).isBetween(expectedDelay.minusSeconds(1), expectedDelay.plusSeconds(1));
    }

    private Question reviewQuestion(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("我想在银行申请转账。");
        question.setContextText("复习句");
        question.setGrammarPoint("请求表达");
        return question;
    }

    private Tag tag(Long id, String type, String code) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setTagType(type);
        tag.setParentId(1000L + id);
        tag.setCode(code);
        tag.setName(code);
        tag.setDescription(code);
        return tag;
    }
}
