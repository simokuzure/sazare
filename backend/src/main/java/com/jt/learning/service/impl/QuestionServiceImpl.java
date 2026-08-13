package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerErrorAnalysisDTO;
import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiArticleGenerationResponseDTO;
import com.jt.learning.dto.AiArticleSentenceDTO;
import com.jt.learning.dto.AiArticleSentenceReviewDTO;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiAnswerRecommendedExpressionDTO;
import com.jt.learning.dto.AiAnswerReviewCommentsDTO;
import com.jt.learning.dto.AiAnswerReviewDTO;
import com.jt.learning.dto.AiAnswerScoresDTO;
import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiAnswerScoringResponseDTO;
import com.jt.learning.dto.AiGeneratedArticleDTO;
import com.jt.learning.dto.AiGeneratedQuestionDTO;
import com.jt.learning.dto.AiQuestionAnswerDTO;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionGenerationResponseDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.dto.QuestionAnswerRequest;
import com.jt.learning.dto.QuestionCreateRequest;
import com.jt.learning.dto.QuestionEnabledRequest;
import com.jt.learning.dto.QuestionEmbeddingBackfillRequest;
import com.jt.learning.dto.QuestionQueryRequest;
import com.jt.learning.dto.QuestionTagRow;
import com.jt.learning.dto.QuestionUpdateRequest;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.ai.AiAnswerScoringClient;
import com.jt.learning.service.ai.AiQuestionClient;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.prompt.AiAnswerScoringPromptBuilder;
import com.jt.learning.service.ai.prompt.AiArticleQuestionPromptBuilder;
import com.jt.learning.service.ai.prompt.AiQuestionPromptBuilder;
import com.jt.learning.service.ai.validation.AiErrorAnalysisValidator;
import com.jt.learning.service.QuestionService;
import com.jt.learning.service.question.QuestionEmbeddingService;
import com.jt.learning.vo.AnswerErrorAnalysisVO;
import com.jt.learning.vo.AnswerRecommendedExpressionVO;
import com.jt.learning.vo.AnswerReviewCommentsVO;
import com.jt.learning.vo.AnswerReviewVO;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.ArticleSentenceReviewVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.QuestionVO;
import com.jt.learning.vo.QuestionEmbeddingBackfillVO;
import com.jt.learning.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final String QUESTION_TYPE = "TRANSLATION_ZH_TO_JA";
    private static final String ARTICLE_QUESTION_TYPE = "TRANSLATION_ZH_TO_JA_ARTICLE";
    private static final String SOURCE_TYPE_AI = "AI";
    private static final String SOURCE_TYPE_MANUAL = "MANUAL";
    private static final String SOURCE_TYPE_REVIEW_DERIVED = "REVIEW_DERIVED";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";
    private static final String TAG_TYPE_GENRE = "GENRE";
    private static final String ANSWER_TYPE_STANDARD = "STANDARD";
    private static final String ANSWER_TYPE_REFERENCE = "REFERENCE";
    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String ANSWER_STATUS_SUBMITTED = "SUBMITTED";
    private static final String ANSWER_STATUS_REVIEWED = "REVIEWED";

    private static final Set<String> VALID_ANSWER_TYPES = Set.of(ANSWER_TYPE_STANDARD, ANSWER_TYPE_REFERENCE);
    private static final Set<String> VALID_FORMALITIES = Set.of("CASUAL", "NEUTRAL", "POLITE", "BUSINESS");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern JAPANESE_TEXT_PATTERN = Pattern.compile("[\\u3040-\\u30ff\\u4e00-\\u9fff]");
    private static final Pattern JAPANESE_KANA_PATTERN = Pattern.compile("[\\u3040-\\u30ff]");
    private static final Pattern ARTICLE_END_PATTERN = Pattern.compile(".*[。？！]$");
    private static final String ARTICLE_SEPARATOR = "\n\n";
    private static final int ARTICLE_MIN_LENGTH = 150;
    private static final int ARTICLE_MAX_LENGTH = 300;
    private static final int ARTICLE_MAX_SENTENCES = 30;

    private final TagMapper tagMapper;
    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionTagMapper questionTagMapper;
    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final AiQuestionPromptBuilder promptBuilder;
    private final AiQuestionClient aiQuestionClient;
    private final AiAnswerScoringPromptBuilder answerScoringPromptBuilder;
    private final AiAnswerScoringClient aiAnswerScoringClient;
    private final AiErrorAnalysisValidator aiErrorAnalysisValidator;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final ObjectMapper objectMapper;

    public QuestionServiceImpl(
            TagMapper tagMapper,
            QuestionMapper questionMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionTagMapper questionTagMapper,
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            ErrorTypeMapper errorTypeMapper,
            AiQuestionPromptBuilder promptBuilder,
            AiQuestionClient aiQuestionClient,
            AiAnswerScoringPromptBuilder answerScoringPromptBuilder,
            AiAnswerScoringClient aiAnswerScoringClient,
            AiErrorAnalysisValidator aiErrorAnalysisValidator,
            QuestionEmbeddingService questionEmbeddingService,
            ObjectMapper objectMapper
    ) {
        this.tagMapper = tagMapper;
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionTagMapper = questionTagMapper;
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.promptBuilder = promptBuilder;
        this.aiQuestionClient = aiQuestionClient;
        this.answerScoringPromptBuilder = answerScoringPromptBuilder;
        this.aiAnswerScoringClient = aiAnswerScoringClient;
        this.aiErrorAnalysisValidator = aiErrorAnalysisValidator;
        this.questionEmbeddingService = questionEmbeddingService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<QuestionVO> generateQuestionsByAi(AiQuestionGenerationRequest request) {
        List<Tag> sceneTags = loadCandidateTags(TAG_TYPE_SCENE, request.sceneTagCodes());
        List<Tag> functionTags = loadCandidateTags(TAG_TYPE_FUNCTION, request.functionTagCodes());
        if (sceneTags.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有可用场景标签");
        }

        List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags);
        List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags);
        List<List<Float>> excludedEmbeddings = embedExcludedSourceTexts(request.excludedSourceTexts());
        List<String> promptExcludedSourceTexts = new ArrayList<>(emptyIfNull(request.excludedSourceTexts()));
        List<PreparedGeneratedQuestion> acceptedQuestions = new ArrayList<>();

        for (int attempt = 0; attempt < 3 && acceptedQuestions.size() < request.questionCount(); attempt++) {
            AiQuestionGenerationRequest attemptRequest = createRetryRequest(
                    request,
                    request.questionCount() - acceptedQuestions.size(),
                    promptExcludedSourceTexts
            );
            AiQuestionPrompt prompt = promptBuilder.build(attemptRequest, sceneTagOptions, functionTagOptions);
            AiQuestionGenerationResponseDTO aiResponse = parseAiResponse(
                    aiQuestionClient.generateQuestions(prompt, attemptRequest, sceneTagOptions, functionTagOptions)
            );
            List<ValidatedGeneratedQuestion> validatedQuestions = validateAiResponse(
                    attemptRequest,
                    aiResponse,
                    toTagMap(sceneTags),
                    toTagMap(functionTags)
            );

            for (ValidatedGeneratedQuestion validatedQuestion : validatedQuestions) {
                List<Float> embedding = questionEmbeddingService.embedQuestion(
                        validatedQuestion.question().sourceText(),
                        validatedQuestion.question().contextText()
                );
                if (isDuplicate(embedding, excludedEmbeddings, acceptedQuestions)) {
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
        return acceptedQuestions.stream().map(this::saveQuestion).toList();
    }

    @Override
    @Transactional
    public QuestionVO generateArticleByAi(AiArticleGenerationRequest request) {
        List<Tag> genreTags = tagMapper.selectEnabledTagsByCodes(
                TAG_TYPE_GENRE,
                List.of(request.genreTagCode())
        );
        if (genreTags.size() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "genreTagCode 不存在、未启用或不是 GENRE 标签");
        }
        Tag genreTag = genreTags.getFirst();
        AiQuestionTagOptionDTO genreOption = new AiQuestionTagOptionDTO(
                genreTag.getCode(),
                genreTag.getName(),
                genreTag.getDescription()
        );
        AiArticleQuestionPromptBuilder articlePromptBuilder = new AiArticleQuestionPromptBuilder(objectMapper);

        for (int attempt = 0; attempt < 3; attempt++) {
            AiQuestionPrompt prompt = articlePromptBuilder.build(request, genreOption);
            AiArticleGenerationResponseDTO response = parseArticleResponse(
                    aiQuestionClient.generateArticle(prompt, request)
            );
            ValidatedArticle article = validateArticleResponse(request, response);
            List<Float> embedding = questionEmbeddingService.embedQuestion(
                    article.sourceText(),
                    article.article().contextText()
            );
            if (!questionEmbeddingService.findSimilarQuestions(embedding, ARTICLE_QUESTION_TYPE).isEmpty()) {
                continue;
            }
            return saveArticle(article, genreTag, embedding);
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 生成文章存在近似重复，补生成后仍未获得可用文章");
    }

    @Override
    @Transactional
    public QuestionEmbeddingBackfillVO backfillQuestionEmbeddings(QuestionEmbeddingBackfillRequest request) {
        return questionEmbeddingService.backfill(request.batchSize());
    }

    @Override
    @Transactional
    public QuestionVO createQuestion(QuestionCreateRequest request) {
        validateQuestionContent(
                request.questionType(),
                request.sourceText(),
                request.contextText(),
                request.grammarPoint()
        );
        List<Tag> tags = loadQuestionTags(request.tagCodes());
        validateSelectedTags(request.questionType(), tags);
        List<AiQuestionAnswerDTO> answers = toAnswerDTOs(request.answers());
        validateAnswers(answers, "题目");

        LocalDateTime now = LocalDateTime.now();
        Question question = new Question();
        question.setQuestionType(QUESTION_TYPE);
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
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);
        questionEmbeddingService.synchronizeEmbedding(question);

        List<QuestionAnswer> savedAnswers = saveAnswers(question.getId(), answers, now);
        saveQuestionTags(question.getId(), tags);
        return toQuestionVO(question, tags, savedAnswers);
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
        if (!ARTICLE_QUESTION_TYPE.equals(question.questionType())) {
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
    @Transactional
    public QuestionVO updateQuestion(Long id, QuestionUpdateRequest request) {
        Question existingQuestion = loadExistingQuestion(id);
        if (!existingQuestion.getQuestionType().equals(request.questionType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "编辑时不能修改 questionType");
        }
        validateQuestionContent(
                request.questionType(),
                request.sourceText(),
                request.contextText(),
                request.grammarPoint()
        );
        List<Tag> tags = loadQuestionTags(request.tagCodes());
        validateSelectedTags(request.questionType(), tags);
        List<AiQuestionAnswerDTO> answers = toAnswerDTOs(request.answers());
        validateAnswers(answers, "题目");
        if (ARTICLE_QUESTION_TYPE.equals(request.questionType())) {
            validateArticleEdit(request.sourceText(), answers);
        }

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
        question.setUpdatedAt(LocalDateTime.now());
        if (questionMapper.updateQuestion(question) == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
        if (!SOURCE_TYPE_REVIEW_DERIVED.equals(question.getSourceType())) {
            questionEmbeddingService.synchronizeEmbedding(question);
        }

        questionAnswerMapper.logicalDeleteByQuestionId(id);
        questionTagMapper.deleteQuestionTagsByQuestionId(id);
        List<QuestionAnswer> savedAnswers = saveAnswers(id, answers, question.getUpdatedAt());
        saveQuestionTags(id, tags);
        return toQuestionVO(question, tags, savedAnswers);
    }

    @Override
    @Transactional
    public void updateQuestionEnabled(Long id, QuestionEnabledRequest request) {
        int updatedRows = questionMapper.updateEnabled(id, request.enabled(), LocalDateTime.now());
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        int updatedRows = questionMapper.logicalDelete(id, LocalDateTime.now());
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或已删除");
        }
    }

    @Override
    @Transactional
    public AnswerReviewVO submitAnswer(Long questionId, AiAnswerScoringRequest request) {
        Question question = questionMapper.selectActiveQuestionById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目不存在或不可用");
        }
        if (SOURCE_TYPE_REVIEW_DERIVED.equals(question.getSourceType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "复习衍生题必须通过复习接口作答");
        }

        List<QuestionAnswer> standardAnswers = questionAnswerMapper.selectActiveAnswersByQuestionId(questionId);
        if (standardAnswers.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "题目没有可用标准答案");
        }

        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本地用户不存在或不可用");
        }

        List<Tag> tags = tagMapper.selectEnabledTagsByQuestionId(questionId);
        List<AiQuestionTagOptionDTO> tagOptions = toTagOptions(tags);
        List<AiErrorTypeOptionDTO> errorTypeOptions = errorTypeMapper.selectEnabledLeafOptions();
        if (errorTypeOptions.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有可用的二级错误类型");
        }
        Map<String, AiErrorTypeOptionDTO> errorTypesByCode = errorTypeOptions.stream()
                .collect(Collectors.toMap(AiErrorTypeOptionDTO::code, option -> option));
        String answerText = normalizeText(request.answerText());
        int maxAnswerLength = ARTICLE_QUESTION_TYPE.equals(question.getQuestionType()) ? 5000 : 2000;
        if (answerText.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "答案不能为空");
        }
        if (answerText.length() > maxAnswerLength) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "答案长度不能超过 " + maxAnswerLength + " 个字符");
        }
        if (ARTICLE_QUESTION_TYPE.equals(question.getQuestionType())
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
        AiAnswerScoringResponseDTO aiResponse = parseAiAnswerScoringResponse(
                aiAnswerScoringClient.scoreAnswer(prompt, normalizedRequest, question, standardAnswers, tagOptions)
        );
        AiAnswerReviewDTO review = validateAnswerReview(
                aiResponse,
                errorTypesByCode,
                answerText,
                question,
                standardAnswers
        );
        BigDecimal totalScore = calculateTotalScore(review.scores());
        UserAnswer userAnswer = saveSubmittedAnswer(user.getId(), questionId, answerText);
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

    private UserAnswer saveSubmittedAnswer(Long userId, Long questionId, String answerText) {
        LocalDateTime now = LocalDateTime.now();
        UserAnswer userAnswer = new UserAnswer();
        userAnswer.setUserId(userId);
        userAnswer.setQuestionId(questionId);
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
            String sourceText,
            String contextText,
            String grammarPoint
    ) {
        if (!QUESTION_TYPE.equals(questionType) && !ARTICLE_QUESTION_TYPE.equals(questionType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "questionType 不合法");
        }
        validateRequiredText(sourceText, "sourceText 不能为空");
        if (!containsChinese(sourceText)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "sourceText 必须包含中文");
        }
        validateRequiredText(contextText, "contextText 不能为空");
        validateRequiredText(grammarPoint, "grammarPoint 不能为空");
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
        if (ARTICLE_QUESTION_TYPE.equals(questionType)) {
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

    private void validateArticleEdit(String sourceText, List<AiQuestionAnswerDTO> answers) {
        if (answers.size() != 1
                || !ANSWER_TYPE_STANDARD.equals(answers.getFirst().answerType())
                || !Boolean.TRUE.equals(answers.getFirst().primaryAnswer())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题必须有且只有 1 个主标准答案");
        }
        List<String> sourceSegments = splitArticleSegments(sourceText, "sourceText");
        List<String> referenceSegments = splitArticleSegments(answers.getFirst().answerText(), "answerText");
        if (sourceSegments.size() != referenceSegments.size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题中文题干与日文参考答案段落数必须一致");
        }
        int characterCount = sourceSegments.stream()
                .flatMapToInt(String::codePoints)
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .map(codePoint -> 1)
                .sum();
        if (characterCount < ARTICLE_MIN_LENGTH || characterCount > ARTICLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题中文长度必须在 150 到 300 个非空白字符之间");
        }
        for (String sourceSegment : sourceSegments) {
            if (!containsChinese(sourceSegment)
                    || containsJapaneseKana(sourceSegment)
                    || !ARTICLE_END_PATTERN.matcher(sourceSegment).matches()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章题每个 sourceText 段落必须是无假名的完整中文句子");
            }
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

    private AiAnswerScoringResponseDTO parseAiAnswerScoringResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分输出为空");
        }

        try {
            JsonNode root = objectMapper.readTree(aiContent);
            validateAnswerScoringRootJson(root);
            return objectMapper.treeToValue(root, AiAnswerScoringResponseDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分输出不是合法 JSON");
        }
    }

    private void validateAnswerScoringRootJson(JsonNode root) {
        if (!root.isObject()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 JSON 顶层必须是对象");
        }

        List<String> fields = new ArrayList<>(root.propertyNames());
        if (!fields.equals(List.of("review"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 JSON 顶层只能包含 review 字段");
        }
        if (!root.get("review").isObject()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 review 必须是对象");
        }
    }

    private AiAnswerReviewDTO validateAnswerReview(
            AiAnswerScoringResponseDTO aiResponse,
            Map<String, AiErrorTypeOptionDTO> errorTypesByCode,
            String answerText,
            Question question,
            List<QuestionAnswer> standardAnswers
    ) {
        if (aiResponse.review() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 review 不能为空");
        }

        AiAnswerReviewDTO review = aiResponse.review();
        validateScores(review.scores());
        validateRequiredText(review.overallComment(), "AI 评分 overallComment 不能为空");
        validateComments(review.comments());
        review = normalizeOptionalReviewContent(review);
        if (ARTICLE_QUESTION_TYPE.equals(question.getQuestionType())) {
            List<String> sourceSegments = splitArticleSegments(question.getSourceText(), "sourceText");
            List<String> referenceSegments = splitArticleSegments(
                    standardAnswers.getFirst().getAnswerText(),
                    "answerText"
            );
            review = normalizeArticleSentenceReviews(
                    review,
                    answerText,
                    sourceSegments,
                    referenceSegments
            );
            review = copyReviewWithErrors(review, aiErrorAnalysisValidator.sanitizeArticle(
                    review.errorAnalysis(),
                    errorTypesByCode,
                    answerText,
                    sourceSegments,
                    referenceSegments
            ));
            validateArticleSentenceReviews(review.sentenceReviews(), sourceSegments, referenceSegments, answerText);
            aiErrorAnalysisValidator.validateArticle(
                    review.errorAnalysis(),
                    errorTypesByCode,
                    answerText,
                    sourceSegments,
                    referenceSegments
            );
        } else {
            review = copyReviewWithErrors(review, aiErrorAnalysisValidator.sanitize(
                    review.errorAnalysis(), errorTypesByCode, answerText));
            aiErrorAnalysisValidator.validate(review.errorAnalysis(), errorTypesByCode, answerText);
        }
        validateRevisionSuggestions(review.revisionSuggestions());
        validateRecommendedExpressions(review.recommendedExpressions());
        return review;
    }

    private AiAnswerReviewDTO normalizeOptionalReviewContent(AiAnswerReviewDTO review) {
        List<String> normalizedRevisionSuggestions = review.revisionSuggestions() == null
                ? List.of()
                : review.revisionSuggestions().stream()
                        .filter(suggestion -> suggestion != null && !suggestion.isBlank())
                        .map(String::trim)
                        .toList();
        List<AiAnswerRecommendedExpressionDTO> normalizedRecommendedExpressions =
                review.recommendedExpressions() == null
                        ? List.of()
                        : review.recommendedExpressions().stream()
                                .filter(this::isCompleteRecommendedExpression)
                                .toList();
        return new AiAnswerReviewDTO(
                review.scores(),
                calculateTotalScore(review.scores()),
                review.overallComment(),
                review.comments(),
                review.sentenceReviews(),
                review.errorAnalysis(),
                normalizedRevisionSuggestions,
                normalizedRecommendedExpressions
        );
    }

    private boolean isCompleteRecommendedExpression(AiAnswerRecommendedExpressionDTO expression) {
        return expression != null
                && expression.expression() != null && !expression.expression().isBlank()
                && expression.usage() != null && !expression.usage().isBlank()
                && VALID_FORMALITIES.contains(expression.formality())
                && expression.note() != null && !expression.note().isBlank();
    }

    private AiAnswerReviewDTO normalizeArticleSentenceReviews(
            AiAnswerReviewDTO review,
            String answerText,
            List<String> sourceSegments,
            List<String> referenceSegments
    ) {
        List<AiArticleSentenceReviewDTO> sentenceReviews = review.sentenceReviews();
        if (sentenceReviews == null || sentenceReviews.size() != sourceSegments.size()) {
            return review;
        }
        Map<Integer, AiArticleSentenceReviewDTO> reviewsByIndex = new LinkedHashMap<>();
        for (AiArticleSentenceReviewDTO sentenceReview : sentenceReviews) {
            if (sentenceReview == null || sentenceReview.sourceSegmentIndex() == null
                    || sentenceReview.sourceSegmentIndex() < 0
                    || sentenceReview.sourceSegmentIndex() >= sourceSegments.size()
                    || reviewsByIndex.putIfAbsent(sentenceReview.sourceSegmentIndex(), sentenceReview) != null) {
                return review;
            }
        }
        List<AiArticleSentenceReviewDTO> normalizedSentenceReviews = new ArrayList<>();
        for (int index = 0; index < sourceSegments.size(); index++) {
            AiArticleSentenceReviewDTO sentenceReview = reviewsByIndex.get(index);
            if (sentenceReview == null) {
                return review;
            }
            normalizedSentenceReviews.add(normalizeArticleSentenceReview(
                    sentenceReview,
                    index,
                    answerText,
                    sourceSegments.get(index),
                    referenceSegments.get(index),
                    review.overallComment()
            ));
        }
        return new AiAnswerReviewDTO(
                review.scores(),
                review.totalScore(),
                review.overallComment(),
                review.comments(),
                List.copyOf(normalizedSentenceReviews),
                review.errorAnalysis(),
                review.revisionSuggestions(),
                review.recommendedExpressions()
        );
    }

    private AiArticleSentenceReviewDTO normalizeArticleSentenceReview(
            AiArticleSentenceReviewDTO sentenceReview,
            int index,
            String answerText,
            String sourceText,
            String referenceText,
            String overallComment
    ) {
        String answerExcerpt = sentenceReview.answerExcerpt();
        if (answerExcerpt != null) {
            answerExcerpt = normalizeText(answerExcerpt);
            if (answerExcerpt.isBlank()) {
                answerExcerpt = null;
            } else if (!answerText.contains(answerExcerpt)) {
                answerExcerpt = answerText;
            }
        }
        return new AiArticleSentenceReviewDTO(
                index,
                sourceText,
                referenceText,
                answerExcerpt,
                referenceText,
                sentenceReview.comment() == null || sentenceReview.comment().isBlank()
                        ? overallComment
                        : sentenceReview.comment().trim()
        );
    }

    private AiAnswerReviewDTO copyReviewWithErrors(
            AiAnswerReviewDTO review,
            List<AiAnswerErrorAnalysisDTO> errorAnalysis
    ) {
        return new AiAnswerReviewDTO(
                review.scores(),
                review.totalScore(),
                review.overallComment(),
                review.comments(),
                review.sentenceReviews(),
                errorAnalysis,
                review.revisionSuggestions(),
                review.recommendedExpressions()
        );
    }

    private void validateArticleSentenceReviews(
            List<AiArticleSentenceReviewDTO> sentenceReviews,
            List<String> sourceSegments,
            List<String> referenceSegments,
            String answerText
    ) {
        if (sourceSegments.size() != referenceSegments.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文章题中文与日文参考段落数不一致");
        }
        if (sentenceReviews == null || sentenceReviews.size() != sourceSegments.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 sentenceReviews 数量不一致");
        }
        for (int index = 0; index < sentenceReviews.size(); index++) {
            AiArticleSentenceReviewDTO review = sentenceReviews.get(index);
            if (review == null || review.sourceSegmentIndex() == null
                    || review.sourceSegmentIndex() != index) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 sentenceReviews 索引不连续");
            }
            if (!sourceSegments.get(index).equals(review.sourceText())
                    || !referenceSegments.get(index).equals(review.referenceText())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分逐句原文或参考答案不一致");
            }
            if (review.answerExcerpt() != null && !review.answerExcerpt().isBlank()
                    && !answerText.contains(review.answerExcerpt())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 answerExcerpt 不属于用户完整答案");
            }
            if (!referenceSegments.get(index).equals(review.revisedText())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 revisedText 必须是对应完整参考句");
            }
            validateRequiredText(review.comment(), "AI 评分 sentenceReviews.comment 不能为空");
        }
    }

    private void validateScores(AiAnswerScoresDTO scores) {
        if (scores == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 scores 不能为空");
        }
        validateScore(scores.grammarVocabularyScore(), "grammarVocabularyScore");
        validateScore(scores.naturalFluencyScore(), "naturalFluencyScore");
        validateScore(scores.scenarioAdaptationScore(), "scenarioAdaptationScore");
        validateScore(scores.informationCompletenessScore(), "informationCompletenessScore");
    }

    private void validateScore(Integer score, String fieldName) {
        if (score == null || score < 0 || score > 100) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 " + fieldName + " 不合法");
        }
    }

    private void validateComments(AiAnswerReviewCommentsDTO comments) {
        if (comments == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 comments 不能为空");
        }
        validateRequiredText(comments.grammarComment(), "AI 评分 grammarComment 不能为空");
        validateRequiredText(comments.vocabularyComment(), "AI 评分 vocabularyComment 不能为空");
        validateRequiredText(comments.naturalnessComment(), "AI 评分 naturalnessComment 不能为空");
        validateRequiredText(comments.scenarioComment(), "AI 评分 scenarioComment 不能为空");
    }

    private void validateRevisionSuggestions(List<String> revisionSuggestions) {
        if (revisionSuggestions == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 revisionSuggestions 不能为空");
        }
        for (String suggestion : revisionSuggestions) {
            validateRequiredText(suggestion, "AI 评分 revisionSuggestions 项不能为空");
        }
    }

    private void validateRecommendedExpressions(List<AiAnswerRecommendedExpressionDTO> recommendedExpressions) {
        if (recommendedExpressions == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 recommendedExpressions 不能为空");
        }
        for (AiAnswerRecommendedExpressionDTO expression : recommendedExpressions) {
            if (expression == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 recommendedExpressions 项不能为空");
            }
            validateRequiredText(expression.expression(), "AI 评分 recommendedExpressions.expression 不能为空");
            validateRequiredText(expression.usage(), "AI 评分 recommendedExpressions.usage 不能为空");
            if (!VALID_FORMALITIES.contains(expression.formality())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 recommendedExpressions.formality 不合法");
            }
            validateRequiredText(expression.note(), "AI 评分 recommendedExpressions.note 不能为空");
        }
    }

    private BigDecimal calculateTotalScore(AiAnswerScoresDTO scores) {
        int sum = scores.grammarVocabularyScore()
                + scores.naturalFluencyScore()
                + scores.scenarioAdaptationScore()
                + scores.informationCompletenessScore();
        return BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }

    private List<Tag> loadCandidateTags(String tagType, List<String> requestedCodes) {
        List<String> codes = normalizeCodes(requestedCodes);
        if (codes.isEmpty()) {
            return tagMapper.selectEnabledTagsByType(tagType);
        }

        List<Tag> tags = tagMapper.selectEnabledTagsByCodes(tagType, codes);
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

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(tag.getCode(), tag.getName(), tag.getDescription()))
                .toList();
    }

    private Map<String, Tag> toTagMap(List<Tag> tags) {
        return tags.stream()
                .collect(Collectors.toMap(Tag::getCode, tag -> tag, (left, right) -> left, LinkedHashMap::new));
    }

    private AiArticleGenerationResponseDTO parseArticleResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章输出为空");
        }
        try {
            JsonNode root = objectMapper.readTree(aiContent);
            if (!root.isObject()
                    || !new LinkedHashSet<>(root.propertyNames()).equals(Set.of("article"))
                    || root.get("article") == null
                    || !root.get("article").isObject()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 JSON 顶层只能包含 article 对象");
            }
            return objectMapper.treeToValue(root, AiArticleGenerationResponseDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章输出不是合法 JSON");
        }
    }

    private ValidatedArticle validateArticleResponse(
            AiArticleGenerationRequest request,
            AiArticleGenerationResponseDTO response
    ) {
        if (response.article() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 article 不能为空");
        }
        var article = response.article();
        if (!ARTICLE_QUESTION_TYPE.equals(article.questionType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 questionType 不合法");
        }
        if (!request.level().equals(article.level()) || !request.difficulty().equals(article.difficulty())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章等级或难度与请求不一致");
        }
        validateRequiredText(article.contextText(), "AI 文章 contextText 不能为空");
        validateRequiredText(article.grammarPoint(), "AI 文章 grammarPoint 不能为空");
        if (article.grammarPoint().length() > 255) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章生词提示不能超过 255 个字符");
        }
        if (article.spoken() == null || article.business() == null || article.exam() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 spoken、business、exam 必须是布尔值");
        }
        if (article.sentences() == null || article.sentences().isEmpty()
                || article.sentences().size() > ARTICLE_MAX_SENTENCES) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章句子数量必须在 1 到 30 之间");
        }

        List<String> sourceSegments = new ArrayList<>();
        List<String> referenceSegments = new ArrayList<>();
        Set<String> uniqueReferences = new LinkedHashSet<>();
        for (int index = 0; index < article.sentences().size(); index++) {
            AiArticleSentenceDTO sentence = article.sentences().get(index);
            if (sentence == null || sentence.index() == null || sentence.index() != index) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章句子索引必须从 0 连续递增");
            }
            String chinese = requireArticleText(sentence.chineseText(), "AI 文章 chineseText 不能为空");
            if (containsLineBreak(chinese)
                    || !containsChinese(chinese)
                    || containsJapaneseKana(chinese)
                    || !ARTICLE_END_PATTERN.matcher(chinese).matches()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 chineseText 必须是无换行、无假名的完整中文句子");
            }
            String japanese = requireArticleText(sentence.japaneseReference(), "AI 文章 japaneseReference 不能为空");
            if (containsLineBreak(japanese) || !containsJapaneseKana(japanese)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 japaneseReference 必须是无换行的日语句子");
            }
            if (!uniqueReferences.add(japanese)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 japaneseReference 不能重复");
            }
            sourceSegments.add(chinese);
            referenceSegments.add(japanese);
        }

        int characterCount = sourceSegments.stream()
                .flatMapToInt(String::codePoints)
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .map(codePoint -> 1)
                .sum();
        if (characterCount < ARTICLE_MIN_LENGTH || characterCount > ARTICLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 中文文章长度必须在 150 到 300 个非空白字符之间");
        }
        return new ValidatedArticle(
                article,
                String.join(ARTICLE_SEPARATOR, sourceSegments),
                String.join(ARTICLE_SEPARATOR, referenceSegments)
        );
    }

    private QuestionVO saveArticle(ValidatedArticle article, Tag genreTag, List<Float> embedding) {
        LocalDateTime now = LocalDateTime.now();
        Question question = new Question();
        question.setQuestionType(ARTICLE_QUESTION_TYPE);
        question.setSourceText(article.sourceText());
        question.setContextText("AI 原创；" + article.article().contextText().trim());
        question.setLevel(article.article().level());
        question.setDifficulty(article.article().difficulty());
        question.setGrammarPoint(article.article().grammarPoint().trim());
        question.setSpoken(article.article().spoken());
        question.setBusiness(article.article().business());
        question.setExam(article.article().exam());
        question.setSourceType(SOURCE_TYPE_AI);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);
        questionEmbeddingService.saveEmbedding(question, embedding);

        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(article.referenceText());
        answer.setAnswerType(ANSWER_TYPE_STANDARD);
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        questionAnswerMapper.insertQuestionAnswer(answer);
        questionTagMapper.insertQuestionTag(question.getId(), genreTag.getId());
        return toQuestionVO(question, List.of(genreTag), List.of());
    }

    private String requireArticleText(String value, String message) {
        validateRequiredText(value, message);
        return value.trim();
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    private boolean containsChinese(String value) {
        return value != null && CHINESE_PATTERN.matcher(value).find();
    }

    private boolean containsJapaneseText(String value) {
        return value != null && JAPANESE_TEXT_PATTERN.matcher(value).find();
    }

    private boolean containsJapaneseKana(String value) {
        return value != null && JAPANESE_KANA_PATTERN.matcher(value).find();
    }

    private AiQuestionGenerationResponseDTO parseAiResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出为空");
        }

        try {
            JsonNode root = objectMapper.readTree(aiContent);
            validateRootJson(root);
            return objectMapper.treeToValue(root, AiQuestionGenerationResponseDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出不是合法 JSON");
        }
    }

    private void validateRootJson(JsonNode root) {
        if (!root.isObject()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出 JSON 顶层必须是对象");
        }

        List<String> fields = new ArrayList<>(root.propertyNames());
        if (!fields.equals(List.of("questions"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出 JSON 顶层只能包含 questions 字段");
        }
        if (!root.get("questions").isArray()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出 questions 必须是数组");
        }
    }

    private List<ValidatedGeneratedQuestion> validateAiResponse(
            AiQuestionGenerationRequest request,
            AiQuestionGenerationResponseDTO aiResponse,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> functionTagMap
    ) {
        if (aiResponse.questions() == null || aiResponse.questions().size() != request.questionCount()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出题目数量不一致");
        }

        Map<String, Tag> allowedTagMap = new LinkedHashMap<>();
        allowedTagMap.putAll(sceneTagMap);
        allowedTagMap.putAll(functionTagMap);

        List<ValidatedGeneratedQuestion> validatedQuestions = new ArrayList<>();
        for (int i = 0; i < aiResponse.questions().size(); i++) {
            AiGeneratedQuestionDTO question = aiResponse.questions().get(i);
            validatedQuestions.add(validateQuestion(request, question, sceneTagMap, allowedTagMap, i));
        }
        return validatedQuestions;
    }

    private List<List<Float>> embedExcludedSourceTexts(List<String> sourceTexts) {
        return emptyIfNull(sourceTexts).stream()
                .map(sourceText -> questionEmbeddingService.embedQuestion(sourceText, ""))
                .toList();
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
                request.extraRequirements()
        );
    }

    private boolean isDuplicate(
            List<Float> embedding,
            List<List<Float>> excludedEmbeddings,
            List<PreparedGeneratedQuestion> acceptedQuestions
    ) {
        if (!questionEmbeddingService.findSimilarQuestions(embedding).isEmpty()) {
            return true;
        }
        if (excludedEmbeddings.stream().anyMatch(excluded -> questionEmbeddingService.isSimilar(embedding, excluded))) {
            return true;
        }
        return acceptedQuestions.stream()
                .anyMatch(accepted -> questionEmbeddingService.isSimilar(embedding, accepted.embedding()));
    }

    private List<String> emptyIfNull(List<String> values) {
        return values == null ? List.of() : values;
    }

    private ValidatedGeneratedQuestion validateQuestion(
            AiQuestionGenerationRequest request,
            AiGeneratedQuestionDTO question,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> allowedTagMap,
            int index
    ) {
        String prefix = "第 " + (index + 1) + " 道题";
        if (question == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + "不能为空");
        }
        if (!QUESTION_TYPE.equals(question.questionType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " questionType 不合法");
        }
        validateRequiredText(question.sourceText(), prefix + " sourceText 不能为空");
        if (!containsChinese(question.sourceText())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " sourceText 必须包含中文");
        }
        validateRequiredText(question.contextText(), prefix + " contextText 不能为空");
        if (!request.level().equals(question.level())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " level 与请求不一致");
        }
        if (!request.difficulty().equals(question.difficulty())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " difficulty 与请求不一致");
        }
        validateRequiredText(question.grammarPoint(), prefix + " grammarPoint 不能为空");
        if (question.spoken() == null || question.business() == null || question.exam() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " spoken、business、exam 必须是布尔值");
        }

        List<Tag> selectedTags = validateTagCodes(question.tagCodes(), sceneTagMap, allowedTagMap, prefix);
        validateAnswers(question.answers(), prefix);
        return new ValidatedGeneratedQuestion(question, selectedTags);
    }

    private void validateRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private List<Tag> validateTagCodes(
            List<String> tagCodes,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> allowedTagMap,
            String prefix
    ) {
        List<String> normalizedCodes = normalizeCodes(tagCodes);
        if (normalizedCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " tagCodes 不能为空");
        }

        boolean hasSceneTag = normalizedCodes.stream().anyMatch(sceneTagMap::containsKey);
        if (!hasSceneTag) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " 至少需要 1 个场景标签");
        }

        List<Tag> selectedTags = new ArrayList<>();
        for (String tagCode : normalizedCodes) {
            Tag tag = allowedTagMap.get(tagCode);
            if (tag == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " 存在非法标签 code: " + tagCode);
            }
            selectedTags.add(tag);
        }
        return selectedTags;
    }

    private void validateAnswers(List<AiQuestionAnswerDTO> answers, String prefix) {
        if (answers == null || answers.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answers 不能为空");
        }

        int primaryStandardCount = 0;
        Set<String> answerTexts = new LinkedHashSet<>();
        for (AiQuestionAnswerDTO answer : answers) {
            if (answer == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answer 不能为空");
            }
            validateAnswer(answer, prefix);
            if (!answerTexts.add(answer.answerText().trim())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answerText 不能重复");
            }
            if (ANSWER_TYPE_STANDARD.equals(answer.answerType()) && Boolean.TRUE.equals(answer.primaryAnswer())) {
                primaryStandardCount++;
            }
        }

        if (primaryStandardCount != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " 必须有且只有 1 个主标准答案");
        }
    }

    private void validateAnswer(AiQuestionAnswerDTO answer, String prefix) {
        validateRequiredText(answer.answerText(), prefix + " answerText 不能为空");
        if (!containsJapaneseText(answer.answerText())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answerText 必须包含日语假名或汉字");
        }
        if (!VALID_ANSWER_TYPES.contains(answer.answerType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answerType 不合法");
        }
        if (answer.primaryAnswer() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " primaryAnswer 不能为空");
        }
        if (answer.sortOrder() == null || answer.sortOrder() < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " sortOrder 不合法");
        }

        if (ANSWER_TYPE_STANDARD.equals(answer.answerType())) {
            if (!Boolean.TRUE.equals(answer.primaryAnswer()) || answer.sortOrder() != 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " STANDARD 主答案规则不合法");
            }
            return;
        }

        if (Boolean.TRUE.equals(answer.primaryAnswer())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " REFERENCE 答案不能是主答案");
        }
    }

    private QuestionVO saveQuestion(PreparedGeneratedQuestion preparedQuestion) {
        ValidatedGeneratedQuestion validatedQuestion = preparedQuestion.question();
        AiGeneratedQuestionDTO generatedQuestion = validatedQuestion.question();
        LocalDateTime now = LocalDateTime.now();

        Question question = new Question();
        question.setQuestionType(QUESTION_TYPE);
        question.setSourceText(generatedQuestion.sourceText().trim());
        question.setContextText(generatedQuestion.contextText().trim());
        question.setLevel(generatedQuestion.level());
        question.setDifficulty(generatedQuestion.difficulty());
        question.setGrammarPoint(generatedQuestion.grammarPoint().trim());
        question.setSpoken(generatedQuestion.spoken());
        question.setBusiness(generatedQuestion.business());
        question.setExam(generatedQuestion.exam());
        question.setSourceType(SOURCE_TYPE_AI);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);
        questionEmbeddingService.saveEmbedding(question, preparedQuestion.embedding());

        List<QuestionAnswer> answers = saveAnswers(question.getId(), generatedQuestion.answers(), now);
        for (Tag tag : validatedQuestion.tags()) {
            questionTagMapper.insertQuestionTag(question.getId(), tag.getId());
        }

        return toQuestionVO(question, validatedQuestion.tags(), answers);
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

    private record ValidatedGeneratedQuestion(
            AiGeneratedQuestionDTO question,
            List<Tag> tags
    ) {
    }

    private record PreparedGeneratedQuestion(
            ValidatedGeneratedQuestion question,
            List<Float> embedding
    ) {
    }

    private record ValidatedArticle(
            AiGeneratedArticleDTO article,
            String sourceText,
            String referenceText
    ) {
    }
}
