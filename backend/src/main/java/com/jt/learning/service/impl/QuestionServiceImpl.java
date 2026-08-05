package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerErrorAnalysisDTO;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiAnswerRecommendedExpressionDTO;
import com.jt.learning.dto.AiAnswerReviewCommentsDTO;
import com.jt.learning.dto.AiAnswerReviewDTO;
import com.jt.learning.dto.AiAnswerScoresDTO;
import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiAnswerScoringResponseDTO;
import com.jt.learning.dto.AiGeneratedQuestionDTO;
import com.jt.learning.dto.AiQuestionAnswerDTO;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionGenerationResponseDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.dto.QuestionAnswerRequest;
import com.jt.learning.dto.QuestionCreateRequest;
import com.jt.learning.dto.QuestionEnabledRequest;
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
import com.jt.learning.service.AiAnswerScoringClient;
import com.jt.learning.service.AiQuestionClient;
import com.jt.learning.service.AiQuestionPrompt;
import com.jt.learning.service.QuestionService;
import com.jt.learning.vo.AnswerErrorAnalysisVO;
import com.jt.learning.vo.AnswerRecommendedExpressionVO;
import com.jt.learning.vo.AnswerReviewCommentsVO;
import com.jt.learning.vo.AnswerReviewVO;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.QuestionVO;
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
    private static final String SOURCE_TYPE_AI = "AI";
    private static final String SOURCE_TYPE_MANUAL = "MANUAL";
    private static final String SOURCE_TYPE_REVIEW_DERIVED = "REVIEW_DERIVED";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";
    private static final String ANSWER_TYPE_STANDARD = "STANDARD";
    private static final String ANSWER_TYPE_REFERENCE = "REFERENCE";
    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String ANSWER_STATUS_SUBMITTED = "SUBMITTED";
    private static final String ANSWER_STATUS_REVIEWED = "REVIEWED";
    private static final String ANSWER_STATUS_FAILED = "FAILED";

    private static final Set<String> VALID_ANSWER_TYPES = Set.of(ANSWER_TYPE_STANDARD, ANSWER_TYPE_REFERENCE);
    private static final Set<String> VALID_FORMALITIES = Set.of("CASUAL", "NEUTRAL", "POLITE", "BUSINESS");
    private static final Pattern CHINESE_PATTERN = Pattern.compile(".*[\\u4e00-\\u9fff].*");
    private static final Pattern JAPANESE_TEXT_PATTERN = Pattern.compile(".*[\\u3040-\\u30ff\\u4e00-\\u9fff].*");

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
        AiQuestionPrompt prompt = promptBuilder.build(request, sceneTagOptions, functionTagOptions);
        AiQuestionGenerationResponseDTO aiResponse = parseAiResponse(
                aiQuestionClient.generateQuestions(prompt, request, sceneTagOptions, functionTagOptions)
        );

        List<ValidatedGeneratedQuestion> validatedQuestions = validateAiResponse(
                request,
                aiResponse,
                toTagMap(sceneTags),
                toTagMap(functionTags)
        );

        List<QuestionVO> savedQuestions = new ArrayList<>();
        for (ValidatedGeneratedQuestion validatedQuestion : validatedQuestions) {
            savedQuestions.add(saveQuestion(validatedQuestion));
        }
        return savedQuestions;
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
        validateSelectedTags(tags);
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
        return questions.isEmpty() ? null : questions.getFirst();
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
        validateQuestionContent(
                request.questionType(),
                request.sourceText(),
                request.contextText(),
                request.grammarPoint()
        );
        List<Tag> tags = loadQuestionTags(request.tagCodes());
        validateSelectedTags(tags);
        List<AiQuestionAnswerDTO> answers = toAnswerDTOs(request.answers());
        validateAnswers(answers, "题目");

        Question question = new Question();
        question.setId(id);
        question.setQuestionType(QUESTION_TYPE);
        question.setSourceText(request.sourceText().trim());
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
    @Transactional(noRollbackFor = BusinessException.class)
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
        UserAnswer userAnswer = saveSubmittedAnswer(user.getId(), questionId, request.answerText().trim());

        try {
            AiQuestionPrompt prompt = answerScoringPromptBuilder.build(
                    question,
                    standardAnswers,
                    tagOptions,
                    errorTypeOptions,
                    request
            );
            AiAnswerScoringResponseDTO aiResponse = parseAiAnswerScoringResponse(
                    aiAnswerScoringClient.scoreAnswer(prompt, request, question, standardAnswers, tagOptions)
            );
            AiAnswerReviewDTO review = validateAnswerReview(aiResponse, errorTypesByCode, userAnswer.getAnswerText());
            BigDecimal totalScore = calculateTotalScore(review.scores());
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
        } catch (BusinessException exception) {
            markAnswerReviewFailed(userAnswer);
            throw exception;
        }
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

    private void markAnswerReviewFailed(UserAnswer userAnswer) {
        LocalDateTime updatedAt = LocalDateTime.now();
        userAnswerMapper.updateFailed(userAnswer.getId(), updatedAt);
        userAnswer.setAnswerStatus(ANSWER_STATUS_FAILED);
        userAnswer.setUpdatedAt(updatedAt);
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
        if (!QUESTION_TYPE.equals(questionType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "questionType 不合法");
        }
        validateRequiredText(sourceText, "sourceText 不能为空");
        if (!CHINESE_PATTERN.matcher(sourceText).matches()) {
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

    private void validateSelectedTags(List<Tag> tags) {
        boolean hasSceneTag = tags.stream()
                .anyMatch(tag -> TAG_TYPE_SCENE.equals(tag.getTagType()));
        if (!hasSceneTag) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "tagCodes 至少需要 1 个场景标签");
        }
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
            String answerText
    ) {
        if (aiResponse.review() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 review 不能为空");
        }

        AiAnswerReviewDTO review = aiResponse.review();
        validateScores(review.scores());
        if (review.totalScore() == null
                || review.totalScore().compareTo(BigDecimal.ZERO) < 0
                || review.totalScore().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分 totalScore 不合法");
        }
        validateRequiredText(review.overallComment(), "AI 评分 overallComment 不能为空");
        validateComments(review.comments());
        aiErrorAnalysisValidator.validate(review.errorAnalysis(), errorTypesByCode, answerText);
        validateRevisionSuggestions(review.revisionSuggestions());
        validateRecommendedExpressions(review.recommendedExpressions());
        return review;
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
        if (!CHINESE_PATTERN.matcher(question.sourceText()).matches()) {
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
        if (!JAPANESE_TEXT_PATTERN.matcher(answer.answerText()).matches()) {
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

    private QuestionVO saveQuestion(ValidatedGeneratedQuestion validatedQuestion) {
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
                review.errorAnalysis().stream()
                        .map(error -> toAnswerErrorAnalysisVO(error, errorTypesByCode))
                        .toList(),
                review.revisionSuggestions().stream().map(String::trim).toList(),
                review.recommendedExpressions().stream().map(this::toAnswerRecommendedExpressionVO).toList(),
                userAnswer.getCreatedAt(),
                userAnswer.getUpdatedAt()
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
}
