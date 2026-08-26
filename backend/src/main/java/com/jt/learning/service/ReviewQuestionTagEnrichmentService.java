package com.jt.learning.service;

import com.jt.learning.config.ReviewTagEnrichmentConfig;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.Tag;
import com.jt.learning.event.ReviewQuestionTagEnrichmentRequestedEvent;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.AiReviewQuestionClient;
import com.jt.learning.service.ai.prompt.AiReviewTagPromptBuilder;
import com.jt.learning.service.ai.validation.ReviewAiResponseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.jt.learning.util.TagHierarchyUtils.secondLevelTags;

@Service
public class ReviewQuestionTagEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ReviewQuestionTagEnrichmentService.class);
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";
    private static final int MAX_ATTEMPTS = 3;
    private static final List<Duration> RETRY_DELAYS = List.of(
            Duration.ofMinutes(1),
            Duration.ofMinutes(3)
    );

    private final TaskExecutor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final DictionaryCacheService dictionaryCacheService;
    private final AiReviewTagPromptBuilder promptBuilder;
    private final AiReviewQuestionClient questionClient;
    private final ReviewAiResponseValidator responseValidator;
    private final ReviewQuestionTagWriter tagWriter;
    private final Set<Long> inFlightQuestionIds = ConcurrentHashMap.newKeySet();

    public ReviewQuestionTagEnrichmentService(
            @Qualifier(ReviewTagEnrichmentConfig.EXECUTOR_BEAN_NAME) TaskExecutor taskExecutor,
            @Qualifier(ReviewTagEnrichmentConfig.SCHEDULER_BEAN_NAME) TaskScheduler taskScheduler,
            QuestionMapper questionMapper,
            QuestionTagMapper questionTagMapper,
            DictionaryCacheService dictionaryCacheService,
            AiReviewTagPromptBuilder promptBuilder,
            AiReviewQuestionClient questionClient,
            ReviewAiResponseValidator responseValidator,
            ReviewQuestionTagWriter tagWriter
    ) {
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
        this.questionMapper = questionMapper;
        this.questionTagMapper = questionTagMapper;
        this.dictionaryCacheService = dictionaryCacheService;
        this.promptBuilder = promptBuilder;
        this.questionClient = questionClient;
        this.responseValidator = responseValidator;
        this.tagWriter = tagWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTagEnrichmentRequested(ReviewQuestionTagEnrichmentRequestedEvent event) {
        Long questionId = event.questionId();
        if (!inFlightQuestionIds.add(questionId)) {
            return;
        }
        submitAttempt(questionId, 1);
    }

    private void submitAttempt(Long questionId, int attempt) {
        try {
            taskExecutor.execute(() -> enrich(questionId, attempt));
        } catch (RuntimeException exception) {
            handleFailure(questionId, attempt, exception);
        }
    }

    private void enrich(Long questionId, int attempt) {
        try {
            Question question = questionMapper.selectQuestionById(questionId);
            if (question == null || questionTagMapper.countByQuestionId(questionId) > 0) {
                finish(questionId);
                return;
            }

            List<Tag> sceneTags = secondLevelTags(dictionaryCacheService.getEnabledTagsByType(TAG_TYPE_SCENE));
            List<Tag> functionTags = secondLevelTags(dictionaryCacheService.getEnabledTagsByType(TAG_TYPE_FUNCTION));
            Map<String, Tag> allowedTagsByCode = new LinkedHashMap<>();
            sceneTags.forEach(tag -> allowedTagsByCode.put(tag.getCode(), tag));
            functionTags.forEach(tag -> allowedTagsByCode.put(tag.getCode(), tag));

            List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags);
            List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags);
            AiQuestionPrompt prompt = promptBuilder.build(question, sceneTagOptions, functionTagOptions);
            List<String> tagCodes = responseValidator.parseTagCodes(
                    questionClient.classifyTags(prompt, sceneTagOptions, functionTagOptions),
                    sceneTags.stream().map(Tag::getCode).collect(Collectors.toSet()),
                    allowedTagsByCode.keySet());
            List<Long> tagIds = tagCodes.stream()
                    .map(String::trim)
                    .map(allowedTagsByCode::get)
                    .map(Tag::getId)
                    .toList();

            boolean saved = tagWriter.saveIfUntagged(questionId, tagIds);
            if (saved) {
                log.info("复习题标签后台补全成功: questionId={}, attempt={}", questionId, attempt);
            }
            finish(questionId);
        } catch (RuntimeException exception) {
            handleFailure(questionId, attempt, exception);
        }
    }

    private void handleFailure(Long questionId, int attempt, RuntimeException exception) {
        if (attempt >= MAX_ATTEMPTS) {
            finish(questionId);
            log.error("复习题标签后台补全最终失败: questionId={}, attempts={}", questionId, attempt, exception);
            return;
        }

        Duration retryDelay = RETRY_DELAYS.get(attempt - 1);
        try {
            taskScheduler.schedule(
                    () -> submitAttempt(questionId, attempt + 1),
                    Instant.now().plus(retryDelay)
            );
            log.warn(
                    "复习题标签后台补全失败，已安排重试: questionId={}, attempt={}, retryDelaySeconds={}",
                    questionId,
                    attempt,
                    retryDelay.toSeconds()
            );
        } catch (RuntimeException schedulingException) {
            finish(questionId);
            schedulingException.addSuppressed(exception);
            log.error(
                    "复习题标签后台重试调度失败: questionId={}, attempt={}",
                    questionId,
                    attempt,
                    schedulingException
            );
        }
    }

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(tag.getCode(), tag.getName(), tag.getDescription()))
                .toList();
    }

    private void finish(Long questionId) {
        inFlightQuestionIds.remove(questionId);
    }
}
