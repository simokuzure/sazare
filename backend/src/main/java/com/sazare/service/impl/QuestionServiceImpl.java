package com.sazare.service.impl;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiAnswerErrorAnalysisDTO;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiArticleRetryContext;
import com.sazare.dto.AiArticleSentenceReviewDTO;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.AiAnswerRecommendedExpressionDTO;
import com.sazare.dto.AiAnswerReviewCommentsDTO;
import com.sazare.dto.AiAnswerReviewDTO;
import com.sazare.dto.AiAnswerScoringRequest;
import com.sazare.dto.AiQuestionAnswerDTO;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.dto.QuestionAnswerRequest;
import com.sazare.dto.QuestionCreateRequest;
import com.sazare.dto.QuestionEnabledRequest;
import com.sazare.dto.QuestionEmbeddingBackfillRequest;
import com.sazare.dto.QuestionEmbeddingMatch;
import com.sazare.dto.QuestionQueryRequest;
import com.sazare.dto.QuestionTagRow;
import com.sazare.dto.QuestionUpdateRequest;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.Tag;
import com.sazare.entity.User;
import com.sazare.entity.UserAnswer;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.QuestionAnswerMapper;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import com.sazare.mapper.ReviewCycleQuestionMapper;
import com.sazare.mapper.TagMapper;
import com.sazare.mapper.UserAnswerMapper;
import com.sazare.mapper.UserMapper;
import com.sazare.service.ai.AiAnswerScoringClient;
import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.prompt.AiAnswerScoringPromptBuilder;
import com.sazare.service.ai.prompt.AiArticleQuestionPromptBuilder;
import com.sazare.service.ai.prompt.AiQuestionPromptBuilder;
import com.sazare.service.ai.validation.AiAnswerScoringResponseValidator;
import com.sazare.service.ai.validation.AiQuestionGenerationResponseValidator;
import com.sazare.service.QuestionService;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.question.GeneratedQuestionPersistenceService;
import com.sazare.service.question.QuestionEmbeddingService;
import com.sazare.vo.AnswerErrorAnalysisVO;
import com.sazare.vo.AnswerRecommendedExpressionVO;
import com.sazare.vo.AnswerReviewCommentsVO;
import com.sazare.vo.AnswerReviewVO;
import com.sazare.vo.AnswerScoresVO;
import com.sazare.vo.ArticleSentenceReviewVO;
import com.sazare.vo.PageVO;
import com.sazare.vo.QuestionAnswerVO;
import com.sazare.vo.QuestionVO;
import com.sazare.vo.QuestionEmbeddingBackfillVO;
import com.sazare.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import tools.jackson.databind.ObjectMapper;

import static com.sazare.service.ai.validation.AiQuestionGenerationResponseValidator.ValidatedArticle;
import static com.sazare.service.ai.validation.AiQuestionGenerationResponseValidator.ValidatedQuestion;
import static com.sazare.util.TagHierarchyUtils.secondLevelTags;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final String SOURCE_TYPE_MANUAL = "MANUAL";
    private static final String SOURCE_TYPE_REVIEW_DERIVED = "REVIEW_DERIVED";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";
    private static final String TAG_TYPE_GENRE = "GENRE";
    private static final String ANSWER_TYPE_STANDARD = "STANDARD";
    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String ANSWER_STATUS_SUBMITTED = "SUBMITTED";
    private static final String ANSWER_STATUS_REVIEWED = "REVIEWED";

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern LATIN_PATTERN = Pattern.compile("\\p{IsLatin}");
    private static final Pattern JAPANESE_KANA_PATTERN = Pattern.compile("[\\u3040-\\u30ff]");
    private static final Pattern CHINESE_ARTICLE_END_PATTERN = Pattern.compile(".*[。？！][\"'”’」』》】）〕〗〙〛〉)]*$");
    private static final Pattern ENGLISH_ARTICLE_END_PATTERN = Pattern.compile(".*[.!?][\"'”’)]*$");
    private static final String ARTICLE_SEPARATOR = "\n\n";
    private static final int ARTICLE_MAX_SENTENCES = 30;

    private final TagMapper tagMapper;
    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionTagMapper questionTagMapper;
    private final ReviewCycleQuestionMapper reviewCycleQuestionMapper;
    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final DictionaryCacheService dictionaryCacheService;
    private final AiQuestionPromptBuilder promptBuilder;
    private final AiQuestionClient aiQuestionClient;
    private final AiAnswerScoringPromptBuilder answerScoringPromptBuilder;
    private final AiAnswerScoringClient aiAnswerScoringClient;
    private final AiAnswerScoringResponseValidator answerScoringResponseValidator;
    private final AiQuestionGenerationResponseValidator generationResponseValidator;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final GeneratedQuestionPersistenceService generatedQuestionPersistenceService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public QuestionServiceImpl(
            TagMapper tagMapper,
            QuestionMapper questionMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionTagMapper questionTagMapper,
            ReviewCycleQuestionMapper reviewCycleQuestionMapper,
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            ErrorTypeMapper errorTypeMapper,
            DictionaryCacheService dictionaryCacheService,
            AiQuestionPromptBuilder promptBuilder,
            AiQuestionClient aiQuestionClient,
            AiAnswerScoringPromptBuilder answerScoringPromptBuilder,
            AiAnswerScoringClient aiAnswerScoringClient,
            AiAnswerScoringResponseValidator answerScoringResponseValidator,
            AiQuestionGenerationResponseValidator generationResponseValidator,
            QuestionEmbeddingService questionEmbeddingService,
            GeneratedQuestionPersistenceService generatedQuestionPersistenceService,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.tagMapper = tagMapper;
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionTagMapper = questionTagMapper;
        this.reviewCycleQuestionMapper = reviewCycleQuestionMapper;
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.dictionaryCacheService = dictionaryCacheService;
        this.promptBuilder = promptBuilder;
        this.aiQuestionClient = aiQuestionClient;
        this.answerScoringPromptBuilder = answerScoringPromptBuilder;
        this.aiAnswerScoringClient = aiAnswerScoringClient;
        this.answerScoringResponseValidator = answerScoringResponseValidator;
        this.generationResponseValidator = generationResponseValidator;
        this.questionEmbeddingService = questionEmbeddingService;
        this.generatedQuestionPersistenceService = generatedQuestionPersistenceService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<QuestionVO> generateQuestionsByAi(AiQuestionGenerationRequest request) {
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        List<Tag> sceneTags = loadCandidateTags(TAG_TYPE_SCENE, request.sceneTagCodes());
        List<Tag> functionTags = loadCandidateTags(TAG_TYPE_FUNCTION, request.functionTagCodes());
        if (sceneTags.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有可用场景标签");
        }

        List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags, direction);
        List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags, direction);
        List<String> promptExcludedSourceTexts = new ArrayList<>(emptyIfNull(request.excludedSourceTexts()));
        List<PreparedGeneratedQuestion> acceptedQuestions = new ArrayList<>();

        for (int attempt = 0; attempt < 3 && acceptedQuestions.size() < request.questionCount(); attempt++) {
            AiQuestionGenerationRequest attemptRequest = createRetryRequest(
                    request,
                    request.questionCount() - acceptedQuestions.size(),
                    promptExcludedSourceTexts
            );
            AiQuestionPrompt prompt = promptBuilder.build(attemptRequest, sceneTagOptions, functionTagOptions);
            List<ValidatedQuestion> validatedQuestions = generationResponseValidator.validateQuestions(
                    attemptRequest,
                    aiQuestionClient.generateQuestions(prompt, attemptRequest, sceneTagOptions, functionTagOptions),
                    toTagMap(sceneTags),
                    toTagMap(functionTags)
            );

            for (ValidatedQuestion validatedQuestion : validatedQuestions) {
                List<Float> embedding = questionEmbeddingService.embedQuestion(
                        validatedQuestion.question().sourceText(),
                        validatedQuestion.question().contextText()
                );
                if (isDuplicate(embedding, acceptedQuestions,
                        validatedQuestion.question().questionType())) {
                    promptExcludedSourceTexts.add(validatedQuestion.question().sourceText().trim());
                    continue;
                }
                acceptedQuestions.add(new PreparedGeneratedQuestion(validatedQuestion, embedding));
                promptExcludedSourceTexts.add(validatedQuestion.question().sourceText().trim());
            }
        }

        if (acceptedQuestions.size() != request.questionCount()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 生成题目存在近似重复，补生成后仍未达到要求数量");
        }
        return transactionTemplate.execute(status -> acceptedQuestions.stream().map(this::saveQuestion).toList());
    }

    @Override
    public QuestionVO generateArticleByAi(AiArticleGenerationRequest request) {
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        Tag genreTag = resolveArticleGenre(request.genreTagCode());
        AiArticleGenerationRequest effectiveRequest = request.genreTagCode() == null
                ? new AiArticleGenerationRequest(
                        request.level(),
                        request.difficulty(),
                        genreTag.getCode(),
                        request.topic(),
                        request.extraRequirements(),
                        request.learningMode(),
                        request.lengthTier()
                )
                : request;
        AiQuestionTagOptionDTO genreOption = new AiQuestionTagOptionDTO(
                genreTag.getCode(),
                direction == TranslationDirection.EN_TO_JA ? genreTag.getNameEn() : genreTag.getName(),
                direction == TranslationDirection.EN_TO_JA ? genreTag.getDescriptionEn() : genreTag.getDescription()
        );
        AiArticleQuestionPromptBuilder articlePromptBuilder = new AiArticleQuestionPromptBuilder(objectMapper);
        AiArticleRetryContext retryContext = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            String seed = UUID.randomUUID().toString();
            AiQuestionPrompt prompt = articlePromptBuilder.build(effectiveRequest, genreOption, seed, retryContext);
            ValidatedArticle article = generationResponseValidator.validateArticle(
                    effectiveRequest,
                    aiQuestionClient.generateArticle(prompt, effectiveRequest, seed),
                    seed
            );
            List<Float> embedding = questionEmbeddingService.embedArticleBody(article.sourceText());
            List<QuestionEmbeddingMatch> matches = questionEmbeddingService.findSimilarQuestions(
                    embedding,
                    direction.articleQuestionType()
            );
            if (!matches.isEmpty()) {
                retryContext = createArticleRetryContext(article.sourceText(), matches);
                continue;
            }
            return transactionTemplate.execute(status -> {
                var saved = generatedQuestionPersistenceService.saveArticle(article, genreTag, embedding);
                return toQuestionVO(saved.question(), saved.tags(), saved.answers());
            });
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 生成文章存在近似重复，补生成后仍未获得可用文章");
    }

    private Tag resolveArticleGenre(String genreTagCode) {
        if (genreTagCode == null) {
            List<Tag> genreTags = dictionaryCacheService.getEnabledTagsByType(TAG_TYPE_GENRE);
            if (genreTags.isEmpty()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有可用文章体裁");
            }
            return genreTags.get(ThreadLocalRandom.current().nextInt(genreTags.size()));
        }
        List<Tag> genreTags = tagMapper.selectEnabledTagsByCodes(TAG_TYPE_GENRE, List.of(genreTagCode));
        if (genreTags.size() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "genreTagCode 不存在、未启用或不是 GENRE 标签");
        }
        return genreTags.getFirst();
    }

    @Override
    public QuestionEmbeddingBackfillVO backfillQuestionEmbeddings(QuestionEmbeddingBackfillRequest request) {
        return questionEmbeddingService.backfill(request.batchSize());
    }

    @Override
    public QuestionVO createQuestion(QuestionCreateRequest request) {
        validateQuestionContent(
                request.questionType(),
                request.sourceText()
        );
        List<Tag> tags = loadQuestionTags(request.tagCodes());
        validateSelectedTags(request.questionType(), tags);
        List<AiQuestionAnswerDTO> answers = toAnswerDTOs(request.answers());
        generationResponseValidator.validateAnswers(answers, "题目");
        validateArticleContentIfNeeded(request.questionType(), request.sourceText(), answers);

        Question question = new Question();
        question.setQuestionType(request.questionType());
        question.setSourceText(request.sourceText().trim());
        question.setContextText(request.contextText().trim());
        question.setLevel(request.level().trim());
        question.setDifficulty(request.difficulty());
        question.setGrammarPoint(request.grammarPoint().trim());
        question.setSpoken(request.spoken());
        question.setBusiness(request.business());
        question.setExam(request.exam());
        question.setSourceType(SOURCE_TYPE_MANUAL);
        question.setEnabled(true);
        question.setDeleted(false);
        List<Float> embedding = embedQuestion(question);
        return transactionTemplate.execute(status -> {
            LocalDateTime persistedAt = LocalDateTime.now();
            question.setCreatedAt(persistedAt);
            question.setUpdatedAt(persistedAt);
            questionMapper.insertQuestion(question);
            questionEmbeddingService.saveEmbedding(question, embedding);
            List<QuestionAnswer> savedAnswers = saveAnswers(question.getId(), answers, persistedAt);
            saveQuestionTags(question.getId(), tags);
            return toQuestionVO(question, tags, savedAnswers);
        });
    }

    @Override
    public PageVO<QuestionVO> listQuestions(QuestionQueryRequest request) {
        QuestionQueryRequest normalizedRequest = normalizeQueryRequest(request);
        long total = questionMapper.countQuestions(normalizedRequest);
        if (total == 0) {
            return new PageVO<>(List.of(), normalizedRequest.page(), normalizedRequest.size(), 0);
        }

        long offset = (long) (normalizedRequest.page() - 1) * normalizedRequest.size();
        List<Long> questionIds = questionMapper.selectQuestionIds(normalizedRequest, normalizedRequest.size(), offset);
        if (questionIds.isEmpty()) {
            return new PageVO<>(List.of(), normalizedRequest.page(), normalizedRequest.size(), total);
        }

        List<QuestionVO> items = loadQuestionVOs(questionIds);
        return new PageVO<>(items, normalizedRequest.page(), normalizedRequest.size(), total);
    }

    @Override
    public QuestionVO getRandomQuestion(QuestionQueryRequest request) {
        QuestionQueryRequest normalizedRequest = normalizeQueryRequest(request);
        Long questionId = questionMapper.selectRandomQuestionId(normalizedRequest);
        if (questionId == null) {
            return null;
        }

        List<QuestionVO> questions = loadQuestionVOs(List.of(questionId));
        if (questions.isEmpty()) {
            return null;
        }
        QuestionVO question = questions.getFirst();
        if (!TranslationDirection.fromQuestionType(question.questionType()).isArticle(question.questionType())) {
            return question;
        }
        return new QuestionVO(
                question.id(),
                question.questionType(),
                question.sourceText(),
                question.contextText(),
                question.level(),
                question.difficulty(),
                question.grammarPoint(),
                question.spoken(),
                question.business(),
                question.exam(),
                question.sourceType(),
                question.enabled(),
                question.tags(),
                List.of(),
                question.createdAt(),
                question.updatedAt()
        );
    }

    private List<QuestionVO> loadQuestionVOs(List<Long> questionIds) {
        List<Question> questions = questionMapper.selectQuestionsByIds(questionIds);
        Map<Long, List<QuestionTagRow>> tagsByQuestionId = tagMapper.selectEnabledTagsByQuestionIds(questionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        QuestionTagRow::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<QuestionAnswer>> answersByQuestionId = questionAnswerMapper.selectActiveAnswersByQuestionIds(questionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        QuestionAnswer::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<QuestionVO> items = questions.stream()
                .map(question -> toQuestionVOFromRows(
                        question,
                        tagsByQuestionId.getOrDefault(question.getId(), List.of()),
                        answersByQuestionId.getOrDefault(question.getId(), List.of())
                ))
                .toList();
        return items;
    }

    @Override
    public QuestionVO getQuestion(Long id) {
        Question question = loadExistingQuestion(id);
        List<Tag> tags = tagMapper.selectEnabledTagsByQuestionId(id);
        List<QuestionAnswer> answers = questionAnswerMapper.selectActiveAnswersByQuestionId(id);
        return toQuestionVO(question, tags, answers);
    }

    @Override
    public QuestionVO updateQuestion(Long id, QuestionUpdateRequest request) {
        Question existingQuestion = loadExistingQuestion(id);
        validateQuestionContent(
                existingQuestion.getQuestionType(),
                request.sourceText()
        );
        List<Tag> tags = loadQuestionTags(request.tagCodes());
        validateSelectedTags(existingQuestion.getQuestionType(), tags);
        List<AiQuestionAnswerDTO> answers = toAnswerDTOs(request.answers());
        generationResponseValidator.validateAnswers(answers, "题目");
        validateArticleContentIfNeeded(existingQuestion.getQuestionType(), request.sourceText(), answers);

        Question question = new Question();
        question.setId(id);
        question.setQuestionType(existingQuestion.getQuestionType());
        question.setSourceText(normalizeText(request.sourceText()));
        question.setContextText(request.contextText().trim());
        question.setLevel(request.level().trim());
        question.setDifficulty(request.difficulty());
        question.setGrammarPoint(request.grammarPoint().trim());
        question.setSpoken(request.spoken());
        question.setBusiness(request.business());
        question.setExam(request.exam());
        question.setSourceType(existingQuestion.getSourceType());
        question.setEnabled(existingQuestion.getEnabled());
        question.setDeleted(false);
        question.setCreatedAt(existingQuestion.getCreatedAt());
        List<Float> embedding = requiresEmbeddingUpdate(existingQuestion, question)
                ? embedQuestion(question)
                : null;
        return transactionTemplate.execute(status -> updateQuestion(question, tags, answers, embedding));
    }

    private QuestionVO updateQuestion(
            Question question,
            List<Tag> tags,
            List<AiQuestionAnswerDTO> answers,
            List<Float> embedding
    ) {
        question.setUpdatedAt(LocalDateTime.now());
        if (questionMapper.updateQuestion(question) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
        if (embedding != null) {
            questionEmbeddingService.saveEmbedding(question, embedding);
        }

        questionAnswerMapper.logicalDeleteByQuestionId(question.getId());
        questionTagMapper.deleteQuestionTagsByQuestionId(question.getId());
        List<QuestionAnswer> savedAnswers = saveAnswers(question.getId(), answers, question.getUpdatedAt());
        saveQuestionTags(question.getId(), tags);
        return toQuestionVO(question, tags, savedAnswers);
    }

    private List<Float> embedQuestion(Question question) {
        TranslationDirection direction = TranslationDirection.fromQuestionType(question.getQuestionType());
        return direction.isArticle(question.getQuestionType())
                ? questionEmbeddingService.embedArticleBody(question.getSourceText())
                : questionEmbeddingService.embedQuestion(question.getSourceText(), question.getContextText());
    }

    private boolean requiresEmbeddingUpdate(Question existingQuestion, Question updatedQuestion) {
        if (SOURCE_TYPE_REVIEW_DERIVED.equals(updatedQuestion.getSourceType())) {
            return false;
        }
        String existingHash = questionEmbeddingService.contentHash(
                existingQuestion.getQuestionType(),
                existingQuestion.getSourceText(),
                existingQuestion.getContextText()
        );
        String updatedHash = questionEmbeddingService.contentHash(
                updatedQuestion.getQuestionType(),
                updatedQuestion.getSourceText(),
                updatedQuestion.getContextText()
        );
        return !Objects.equals(existingHash, updatedHash);
    }

    @Override
    @Transactional
    public void updateQuestionEnabled(Long id, QuestionEnabledRequest request) {
        if (!request.enabled()) {
            ensureNotInProgressReview(id, "停用");
        }
        int updatedRows = questionMapper.updateEnabled(id, request.enabled(), LocalDateTime.now());
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        ensureNotInProgressReview(id, "删除");
        int updatedRows = questionMapper.logicalDelete(id, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
    }

    private void ensureNotInProgressReview(Long questionId, String action) {
        if (reviewCycleQuestionMapper.existsInProgressCycleByQuestionId(questionId)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "题目正在复习周期中，不能" + action
            );
        }
    }

    @Override
    public AnswerReviewVO submitAnswer(Long questionId, AiAnswerScoringRequest request) {
        Question question = questionMapper.selectActiveQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或不可用");
        }
        List<QuestionAnswer> standardAnswers = questionAnswerMapper.selectActiveAnswersByQuestionId(questionId);
        if (standardAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目没有可用标准答案");
        }

        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本地用户不存在或不可用");
        }

        TranslationDirection direction = TranslationDirection.fromQuestionType(question.getQuestionType());
        List<Tag> tags = tagMapper.selectEnabledTagsByQuestionId(questionId);
        List<AiQuestionTagOptionDTO> tagOptions = toTagOptions(tags, direction);
        List<AiErrorTypeOptionDTO> errorTypeOptions = localizeErrorTypes(
                dictionaryCacheService.getEnabledLeafErrorTypes(), direction);
        if (errorTypeOptions.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有可用的二级错误类型");
        }
        Map<String, AiErrorTypeOptionDTO> errorTypesByCode = errorTypeOptions.stream()
                .collect(Collectors.toMap(AiErrorTypeOptionDTO::code, option -> option));
        String answerText = normalizeText(request.answerText());
        int maxAnswerLength = direction.isArticle(question.getQuestionType()) ? 5000 : 2000;
        if (answerText.length() > maxAnswerLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "答案长度不能超过 " + maxAnswerLength + " 个字符");
        }
        if (direction.isArticle(question.getQuestionType())
                && !containsJapaneseKana(answerText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章答案必须包含日语假名");
        }
        AiAnswerScoringRequest normalizedRequest = new AiAnswerScoringRequest(answerText);
        AiQuestionPrompt prompt = answerScoringPromptBuilder.build(
                question,
                standardAnswers,
                tagOptions,
                errorTypeOptions,
                normalizedRequest
        );
        AiAnswerReviewDTO review = answerScoringResponseValidator.validate(
                aiAnswerScoringClient.scoreAnswer(prompt, normalizedRequest, question, standardAnswers, tagOptions),
                errorTypesByCode,
                answerText,
                question,
                standardAnswers
        );
        return transactionTemplate.execute(status -> saveReviewedAnswer(
                user.getId(), questionId, answerText, direction.learningMode(), review, review.totalScore(),
                errorTypesByCode));
    }

    private AnswerReviewVO saveReviewedAnswer(
            Long userId,
            Long questionId,
            String answerText,
            String learningMode,
            AiAnswerReviewDTO review,
            BigDecimal totalScore,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        UserAnswer userAnswer = saveSubmittedAnswer(userId, questionId, answerText, learningMode);
        LocalDateTime updatedAt = LocalDateTime.now();
        userAnswerMapper.updateReviewed(
                userAnswer.getId(),
                review.scores().grammarVocabularyScore(),
                review.scores().naturalFluencyScore(),
                review.scores().scenarioAdaptationScore(),
                review.scores().informationCompletenessScore(),
                totalScore,
                review.overallComment().trim(),
                updatedAt
        );
        userAnswer.setAnswerStatus(ANSWER_STATUS_REVIEWED);
        userAnswer.setGrammarVocabularyScore(review.scores().grammarVocabularyScore());
        userAnswer.setNaturalFluencyScore(review.scores().naturalFluencyScore());
        userAnswer.setScenarioAdaptationScore(review.scores().scenarioAdaptationScore());
        userAnswer.setInformationCompletenessScore(review.scores().informationCompletenessScore());
        userAnswer.setTotalScore(totalScore);
        userAnswer.setAiOverallComment(review.overallComment().trim());
        userAnswer.setUpdatedAt(updatedAt);
        return toAnswerReviewVO(userAnswer, review, errorTypesByCode);
    }

    private UserAnswer saveSubmittedAnswer(Long userId, Long questionId, String answerText, String learningMode) {
        LocalDateTime now = LocalDateTime.now();
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setUserId(userId);
        userAnswer.setQuestionId(questionId);
        userAnswer.setLearningMode(learningMode);
        userAnswer.setAnswerText(answerText);
        userAnswer.setAnswerStatus(ANSWER_STATUS_SUBMITTED);
        userAnswer.setDeleted(false);
        userAnswer.setCreatedAt(now);
        userAnswer.setUpdatedAt(now);
        userAnswerMapper.insertUserAnswer(userAnswer);
        return userAnswer;
    }

    private QuestionQueryRequest normalizeQueryRequest(QuestionQueryRequest request) {
        return new QuestionQueryRequest(
                request.questionType(),
                request.level(),
                request.difficulty(),
                normalizeQueryTagCodes(request.tagCodes()),
                request.spoken(),
                request.business(),
                request.exam(),
                request.sourceType(),
                request.enabled(),
                request.page(),
                request.size()
        );
    }

    private List<String> normalizeQueryTagCodes(List<String> tagCodes) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return List.of();
        }

        List<String> normalizedCodes = tagCodes.stream()
                .flatMap(tagCode -> List.of(tagCode.split(",")).stream())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (normalizedCodes.stream().anyMatch(String::isBlank)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tagCodes 不能包含空值");
        }
        return normalizedCodes;
    }

    private Question loadExistingQuestion(Long id) {
        Question question = questionMapper.selectQuestionById(id);
        if (question == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
        return question;
    }

    private void validateQuestionContent(
            String questionType,
            String sourceText
    ) {
        TranslationDirection direction = TranslationDirection.fromQuestionType(questionType);
        validateSourceLanguage(direction, sourceText);
    }

    private void validateSourceLanguage(TranslationDirection direction, String sourceText) {
        if (direction == TranslationDirection.ZH_TO_JA) {
            if (!containsChinese(sourceText) || containsJapaneseKana(sourceText)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "sourceText 必须包含中文且不能包含日语假名");
            }
            return;
        }
        if (!containsLatin(sourceText) || containsChinese(sourceText) || containsJapaneseKana(sourceText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "sourceText 必须包含英文且不能包含中文或日语假名");
        }
    }

    private List<Tag> loadQuestionTags(List<String> tagCodes) {
        List<String> normalizedCodes = normalizeCodes(tagCodes);
        if (normalizedCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tagCodes 不能为空");
        }

        List<Tag> tags = tagMapper.selectEnabledTagsByAnyCodes(normalizedCodes);
        Map<String, Tag> tagMap = tags.stream()
                .collect(Collectors.toMap(Tag::getCode, tag -> tag, (left, right) -> left, LinkedHashMap::new));
        List<String> missingCodes = normalizedCodes.stream()
                .filter(code -> !tagMap.containsKey(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "标签 code 不存在或未启用: " + missingCodes);
        }
        return normalizedCodes.stream()
                .map(tagMap::get)
                .toList();
    }

    private void validateSelectedTags(String questionType, List<Tag> tags) {
        if (TranslationDirection.fromQuestionType(questionType).isArticle(questionType)) {
            long genreTagCount = tags.stream()
                    .filter(tag -> TAG_TYPE_GENRE.equals(tag.getTagType()))
                    .count();
            if (genreTagCount != 1) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题必须且只能选择 1 个体裁标签");
            }
            return;
        }
        boolean hasSceneTag = tags.stream()
                .anyMatch(tag -> TAG_TYPE_SCENE.equals(tag.getTagType()));
        if (!hasSceneTag) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tagCodes 至少需要 1 个场景标签");
        }
    }

    private void validateArticleContentIfNeeded(
            String questionType,
            String sourceText,
            List<AiQuestionAnswerDTO> answers
    ) {
        TranslationDirection direction = TranslationDirection.fromQuestionType(questionType);
        if (direction.isArticle(questionType)) {
            validateArticleContent(direction, sourceText, answers);
        }
    }

    private void validateArticleContent(
            TranslationDirection direction,
            String sourceText,
            List<AiQuestionAnswerDTO> answers
    ) {
        if (answers.size() != 1
                || !ANSWER_TYPE_STANDARD.equals(answers.getFirst().answerType())
                || !Boolean.TRUE.equals(answers.getFirst().primaryAnswer())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题必须有且只有 1 个主标准答案");
        }
        List<String> sourceSegments = splitArticleSegments(sourceText, "sourceText");
        List<String> referenceSegments = splitArticleSegments(answers.getFirst().answerText(), "answerText");
        if (sourceSegments.size() != referenceSegments.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题原文与日文参考答案段落数必须一致");
        }
        for (String sourceSegment : sourceSegments) {
            validateArticleSourceSegment(direction, sourceSegment);
        }
        Set<String> uniqueReferences = new LinkedHashSet<>();
        for (String referenceSegment : referenceSegments) {
            if (!containsJapaneseKana(referenceSegment)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题每个 answerText 段落必须包含日语假名");
            }
            if (!uniqueReferences.add(referenceSegment)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题日文参考句不能重复");
            }
        }
    }

    private void validateArticleSourceSegment(TranslationDirection direction, String sourceSegment) {
        if (direction == TranslationDirection.ZH_TO_JA) {
            if (!containsChinese(sourceSegment)
                    || containsJapaneseKana(sourceSegment)
                    || !CHINESE_ARTICLE_END_PATTERN.matcher(sourceSegment).matches()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题每个 sourceText 段落必须是无假名的完整中文句子");
            }
            return;
        }
        if (!containsLatin(sourceSegment)
                || containsChinese(sourceSegment)
                || containsJapaneseKana(sourceSegment)
                || !ENGLISH_ARTICLE_END_PATTERN.matcher(sourceSegment).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题每个 sourceText 段落必须是不含中日文字的完整英文句子");
        }
    }

    private List<String> splitArticleSegments(String text, String fieldName) {
        String normalized = normalizeText(text);
        List<String> segments = Pattern.compile("\\n\\s*\\n")
                .splitAsStream(normalized)
                .map(String::trim)
                .toList();
        if (segments.isEmpty() || segments.size() > ARTICLE_MAX_SENTENCES
                || segments.stream().anyMatch(String::isBlank)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, fieldName + " 文章段落不合法");
        }
        return segments;
    }

    private String normalizeText(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private List<AiQuestionAnswerDTO> toAnswerDTOs(List<QuestionAnswerRequest> requests) {
        return requests.stream()
                .map(request -> new AiQuestionAnswerDTO(
                        request.answerText(),
                        request.answerType(),
                        request.primaryAnswer(),
                        request.sortOrder()
                ))
                .toList();
    }

    private void saveQuestionTags(Long questionId, List<Tag> tags) {
        for (Tag tag : tags) {
            questionTagMapper.insertQuestionTag(questionId, tag.getId());
        }
    }

    private List<Tag> loadCandidateTags(String tagType, List<String> requestedCodes) {
        List<String> codes = normalizeCodes(requestedCodes);
        if (codes.isEmpty()) {
            return secondLevelTags(dictionaryCacheService.getEnabledTagsByType(tagType));
        }

        List<Tag> tags = secondLevelTags(tagMapper.selectEnabledTagsByCodes(tagType, codes));
        Set<String> foundCodes = tags.stream()
                .map(Tag::getCode)
                .collect(Collectors.toSet());
        List<String> missingCodes = codes.stream()
                .filter(code -> !foundCodes.contains(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, tagType + " 标签 code 不存在或未启用: " + missingCodes);
        }
        return tags;
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags, TranslationDirection direction) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(
                        tag.getCode(),
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
                option.parentCode(),
                direction.displayText(option.parentName(), option.parentNameEn())
        )).toList();
    }

    private Map<String, Tag> toTagMap(List<Tag> tags) {
        return tags.stream()
                .collect(Collectors.toMap(Tag::getCode, tag -> tag, (left, right) -> left, LinkedHashMap::new));
    }

    private AiArticleRetryContext createArticleRetryContext(
            String rejectedArticle,
            List<QuestionEmbeddingMatch> matches
    ) {
        double highestSimilarity = matches.getFirst().similarity();
        String rejectionReason = String.format(
                Locale.ROOT,
                "正文向量与 %d 篇历史文章相似，最高相似度为 %.4f，达到拒绝阈值 %.2f。",
                matches.size(),
                highestSimilarity,
                QuestionEmbeddingService.SIMILARITY_THRESHOLD
        );
        return new AiArticleRetryContext(rejectionReason, rejectedArticle, List.copyOf(matches));
    }

    private boolean containsChinese(String value) {
        return value != null && CHINESE_PATTERN.matcher(value).find();
    }

    private boolean containsLatin(String value) {
        return value != null && LATIN_PATTERN.matcher(value).find();
    }

    private boolean containsJapaneseKana(String value) {
        return value != null && JAPANESE_KANA_PATTERN.matcher(value).find();
    }

    private AiQuestionGenerationRequest createRetryRequest(
            AiQuestionGenerationRequest request,
            int questionCount,
            List<String> excludedSourceTexts
    ) {
        return new AiQuestionGenerationRequest(
                questionCount,
                request.level(),
                request.difficulty(),
                request.sceneTagCodes(),
                request.functionTagCodes(),
                List.copyOf(excludedSourceTexts),
                request.extraRequirements(),
                request.learningMode()
        );
    }

    private boolean isDuplicate(
            List<Float> embedding,
            List<PreparedGeneratedQuestion> acceptedQuestions,
            String questionType
    ) {
        List<QuestionEmbeddingMatch> matches = TranslationDirection.ZH_TO_JA.shortQuestionType().equals(questionType)
                ? questionEmbeddingService.findSimilarQuestions(embedding)
                : questionEmbeddingService.findSimilarQuestions(embedding, questionType);
        if (!matches.isEmpty()) {
            return true;
        }
        return acceptedQuestions.stream()
                .anyMatch(accepted -> questionEmbeddingService.isSimilar(embedding, accepted.embedding()));
    }

    private List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }

    private QuestionVO saveQuestion(PreparedGeneratedQuestion preparedQuestion) {
        var saved = generatedQuestionPersistenceService.saveShort(
                preparedQuestion.question(),
                preparedQuestion.embedding()
        );
        return toQuestionVO(saved.question(), saved.tags(), saved.answers());
    }

    private List<QuestionAnswer> saveAnswers(Long questionId, List<AiQuestionAnswerDTO> answerDTOs, LocalDateTime now) {
        List<QuestionAnswer> answers = new ArrayList<>();
        for (AiQuestionAnswerDTO answerDTO : answerDTOs) {
            QuestionAnswer answer = new QuestionAnswer();
            answer.setQuestionId(questionId);
            answer.setAnswerText(answerDTO.answerText().trim());
            answer.setAnswerType(answerDTO.answerType());
            answer.setPrimaryAnswer(answerDTO.primaryAnswer());
            answer.setSortOrder(answerDTO.sortOrder());
            answer.setDeleted(false);
            answer.setCreatedAt(now);
            answer.setUpdatedAt(now);
            questionAnswerMapper.insertQuestionAnswer(answer);
            answers.add(answer);
        }
        return answers;
    }

    private QuestionVO toQuestionVO(Question question, List<Tag> tags, List<QuestionAnswer> answers) {
        return new QuestionVO(
                question.getId(),
                question.getQuestionType(),
                question.getSourceText(),
                question.getContextText(),
                question.getLevel(),
                question.getDifficulty(),
                question.getGrammarPoint(),
                question.getSpoken(),
                question.getBusiness(),
                question.getExam(),
                question.getSourceType(),
                question.getEnabled(),
                tags.stream().map(this::toTagVO).toList(),
                answers.stream().map(this::toAnswerVO).toList(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    private QuestionVO toQuestionVOFromRows(
            Question question,
            List<QuestionTagRow> tags,
            List<QuestionAnswer> answers
    ) {
        return new QuestionVO(
                question.getId(),
                question.getQuestionType(),
                question.getSourceText(),
                question.getContextText(),
                question.getLevel(),
                question.getDifficulty(),
                question.getGrammarPoint(),
                question.getSpoken(),
                question.getBusiness(),
                question.getExam(),
                question.getSourceType(),
                question.getEnabled(),
                tags.stream().map(this::toTagVO).toList(),
                answers.stream().map(this::toAnswerVO).toList(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    private TagVO toTagVO(Tag tag) {
        return new TagVO(
                tag.getId(),
                tag.getTagType(),
                tag.getParentId(),
                tag.getCode(),
                tag.getName(),
                tag.getDescription(),
                tag.getNameEn(),
                tag.getDescriptionEn(),
                tag.getSortOrder()
        );
    }

    private TagVO toTagVO(QuestionTagRow tag) {
        return new TagVO(
                tag.getId(),
                tag.getTagType(),
                tag.getParentId(),
                tag.getCode(),
                tag.getName(),
                tag.getDescription(),
                tag.getNameEn(),
                tag.getDescriptionEn(),
                tag.getSortOrder()
        );
    }

    private AnswerReviewVO toAnswerReviewVO(
            UserAnswer userAnswer,
            AiAnswerReviewDTO review,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        return new AnswerReviewVO(
                userAnswer.getId(),
                userAnswer.getQuestionId(),
                userAnswer.getAnswerText(),
                userAnswer.getAnswerStatus(),
                toAnswerScoresVO(userAnswer),
                userAnswer.getTotalScore(),
                userAnswer.getAiOverallComment(),
                toAnswerReviewCommentsVO(review.comments()),
                review.sentenceReviews() == null
                        ? null
                        : review.sentenceReviews().stream()
                                .map(AiArticleSentenceReviewDTO::revisedText)
                                .collect(Collectors.joining(ARTICLE_SEPARATOR)),
                review.sentenceReviews() == null
                        ? List.of()
                        : review.sentenceReviews().stream()
                                .map(this::toArticleSentenceReviewVO)
                                .toList(),
                review.errorAnalysis().stream()
                        .map(error -> toAnswerErrorAnalysisVO(error, errorTypesByCode))
                        .toList(),
                review.revisionSuggestions().stream().map(String::trim).toList(),
                review.recommendedExpressions().stream().map(this::toAnswerRecommendedExpressionVO).toList(),
                userAnswer.getCreatedAt(),
                userAnswer.getUpdatedAt()
        );
    }

    private ArticleSentenceReviewVO toArticleSentenceReviewVO(AiArticleSentenceReviewDTO review) {
        return new ArticleSentenceReviewVO(
                review.sourceSegmentIndex(),
                review.sourceText(),
                review.referenceText(),
                review.answerExcerpt(),
                review.revisedText(),
                review.comment().trim()
        );
    }

    private AnswerScoresVO toAnswerScoresVO(UserAnswer userAnswer) {
        return new AnswerScoresVO(
                userAnswer.getGrammarVocabularyScore(),
                userAnswer.getNaturalFluencyScore(),
                userAnswer.getScenarioAdaptationScore(),
                userAnswer.getInformationCompletenessScore()
        );
    }

    private AnswerReviewCommentsVO toAnswerReviewCommentsVO(AiAnswerReviewCommentsDTO comments) {
        return new AnswerReviewCommentsVO(
                comments.grammarComment().trim(),
                comments.vocabularyComment().trim(),
                comments.naturalnessComment().trim(),
                comments.scenarioComment().trim()
        );
    }

    private AnswerErrorAnalysisVO toAnswerErrorAnalysisVO(
            AiAnswerErrorAnalysisDTO error,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode
    ) {
        AiErrorTypeOptionDTO errorType = errorTypesByCode.get(error.errorTypeCode());
        return new AnswerErrorAnalysisVO(
                errorType.code(),
                errorType.id(),
                errorType.code(),
                errorType.name(),
                error.original().trim(),
                error.issue().trim(),
                error.suggestion().trim(),
                error.severity(),
                error.suggestedUserErrorTypeName().trim(),
                error.suggestedUserErrorTypeDescription().trim()
        );
    }

    private AnswerRecommendedExpressionVO toAnswerRecommendedExpressionVO(AiAnswerRecommendedExpressionDTO expression) {
        return new AnswerRecommendedExpressionVO(
                expression.expression().trim(),
                expression.usage().trim(),
                expression.formality(),
                expression.note().trim()
        );
    }

    private QuestionAnswerVO toAnswerVO(QuestionAnswer answer) {
        return new QuestionAnswerVO(
                answer.getId(),
                answer.getAnswerText(),
                answer.getAnswerType(),
                answer.getPrimaryAnswer(),
                answer.getSortOrder()
        );
    }

    private record PreparedGeneratedQuestion(
            ValidatedQuestion question,
            List<Float> embedding
    ) {
    }
}
