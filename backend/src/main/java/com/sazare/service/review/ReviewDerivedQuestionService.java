package com.sazare.service.review;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiQuestionAnswerDTO;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.dto.AiReviewGeneratedQuestionDTO;
import com.sazare.entity.ErrorType;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.ReviewCycle;
import com.sazare.entity.ReviewCycleQuestion;
import com.sazare.entity.Tag;
import com.sazare.entity.UserErrorType;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.QuestionAnswerMapper;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import com.sazare.mapper.ReviewCycleQuestionMapper;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.AiReviewQuestionClient;
import com.sazare.service.ai.prompt.AiReviewQuestionPromptBuilder;
import com.sazare.service.ai.validation.ReviewAiResponseValidator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.sazare.util.TagHierarchyUtils.secondLevelTags;

@Service
public class ReviewDerivedQuestionService {

    private static final String QUESTION_ORIGINAL = "ORIGINAL";
    private static final String QUESTION_DERIVED = "DERIVED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String SOURCE_REVIEW_DERIVED = "REVIEW_DERIVED";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";

    private final ReviewCycleQuestionMapper reviewCycleQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionTagMapper questionTagMapper;
    private final DictionaryCacheService dictionaryCacheService;
    private final AiReviewQuestionPromptBuilder questionPromptBuilder;
    private final AiReviewQuestionClient questionClient;
    private final ReviewAiResponseValidator responseValidator;

    public ReviewDerivedQuestionService(
            ReviewCycleQuestionMapper reviewCycleQuestionMapper,
            QuestionMapper questionMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionTagMapper questionTagMapper,
            DictionaryCacheService dictionaryCacheService,
            AiReviewQuestionPromptBuilder questionPromptBuilder,
            AiReviewQuestionClient questionClient,
            ReviewAiResponseValidator responseValidator
    ) {
        this.reviewCycleQuestionMapper = reviewCycleQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionTagMapper = questionTagMapper;
        this.dictionaryCacheService = dictionaryCacheService;
        this.questionPromptBuilder = questionPromptBuilder;
        this.questionClient = questionClient;
        this.responseValidator = responseValidator;
    }

    public PreparedDerivedQuestion prepare(
            ReviewCycle cycle,
            UserErrorType userErrorType,
            ErrorType errorType
    ) {
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
                .collect(Collectors.toMap(Tag::getCode, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        TranslationDirection direction = TranslationDirection.fromLearningMode(userErrorType.getLearningMode());
        List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags, direction);
        List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags, direction);
        AiQuestionPrompt prompt = questionPromptBuilder.build(
                userErrorType, errorType, questions, answersByQuestionId, sceneTagOptions, functionTagOptions);
        Set<String> sourceTexts = questions.stream().map(Question::getSourceText).collect(Collectors.toSet());
        AiReviewGeneratedQuestionDTO generated = responseValidator.parseQuestion(
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
        return new PreparedDerivedQuestion(baseQuestion, generated, allowedTagsByCode);
    }

    public GeneratedQuestionIds save(
            ReviewCycle cycle,
            PreparedDerivedQuestion prepared,
            LocalDateTime now
    ) {
        List<ReviewCycleQuestion> cycleQuestions = reviewCycleQuestionMapper.selectAllByCycleId(cycle.getId());
        Question derived = newDerivedQuestion(prepared.baseQuestion(), prepared.generated(), now);
        questionMapper.insertQuestion(derived);
        for (AiQuestionAnswerDTO answerDTO : prepared.generated().answers()) {
            questionAnswerMapper.insertQuestionAnswer(newDerivedAnswer(derived.getId(), answerDTO, now));
        }
        for (String tagCode : prepared.generated().tagCodes()) {
            questionTagMapper.insertQuestionTag(
                    derived.getId(), prepared.allowedTagsByCode().get(tagCode.trim()).getId());
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

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags, TranslationDirection direction) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(
                        tag.getCode(),
                        direction.displayText(tag.getName(), tag.getNameEn()),
                        direction.displayText(tag.getDescription(), tag.getDescriptionEn())
                ))
                .toList();
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
    }

    public record PreparedDerivedQuestion(
            Question baseQuestion,
            AiReviewGeneratedQuestionDTO generated,
            Map<String, Tag> allowedTagsByCode
    ) {
    }

    public record GeneratedQuestionIds(Long questionId, Long cycleQuestionId) {
    }
}
