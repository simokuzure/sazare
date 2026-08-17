package com.jt.learning.service.impl;

import com.jt.learning.dto.UserAnswerDetailRow;
import com.jt.learning.dto.UserAnswerErrorConfirmItemRequest;
import com.jt.learning.dto.UserAnswerErrorConfirmRequest;
import com.jt.learning.dto.ReviewCardCreateRequest;
import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.dto.UserErrorTypeListItemRow;
import com.jt.learning.dto.UserErrorTypeQueryRequest;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.ReviewCard;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.entity.UserAnswerError;
import com.jt.learning.entity.UserErrorType;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerErrorMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserErrorTypeMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.service.ReviewService;
import com.jt.learning.service.ai.AiQuestionPrompt;
import com.jt.learning.service.ai.AiReviewQuestionClient;
import com.jt.learning.service.ai.prompt.AiReviewTagPromptBuilder;
import com.jt.learning.service.ai.validation.ReviewAiResponseValidator;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.ReviewCardCreatedVO;
import com.jt.learning.vo.TagVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerErrorVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import com.jt.learning.vo.UserErrorTypeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class UserAnswerServiceImpl implements UserAnswerService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String ANSWER_STATUS_REVIEWED = "REVIEWED";
    private static final String USER_ERROR_TYPE_STATUS_ACTIVE = "ACTIVE";
    private static final String CONFIRM_MODE_NEW = "NEW_USER_ERROR_TYPE";
    private static final String CONFIRM_MODE_EXISTING = "EXISTING_USER_ERROR_TYPE";
    private static final String ARTICLE_QUESTION_TYPE = "TRANSLATION_ZH_TO_JA_ARTICLE";
    private static final String SHORT_QUESTION_TYPE = "TRANSLATION_ZH_TO_JA";
    private static final String SOURCE_TYPE_REVIEW_DERIVED = "REVIEW_DERIVED";
    private static final String DEFAULT_CORRECTION_REVIEW_LEVEL = "N3";
    private static final int DEFAULT_CORRECTION_REVIEW_DIFFICULTY = 3;
    private static final String CUSTOM_REVIEW_ERROR_TYPE_CODE = "UNNATURAL_EXPRESSION";
    private static final String CUSTOM_REVIEW_TYPE_DESCRIPTION = "用户自定义复习重点。";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";
    private static final Set<String> TRANSLATION_ONLY_ERROR_CODES = Set.of(
            "OMISSION", "MISTRANSLATION", "ADDITION", "FALSE_FRIEND", "CHINESE_CALQUE"
    );
    private static final Pattern JAPANESE_KANA_PATTERN = Pattern.compile("[\\p{IsHiragana}\\p{IsKatakana}]");
    private static final Pattern HAN_PATTERN = Pattern.compile("\\p{IsHan}");

    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final TagMapper tagMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionMapper questionMapper;
    private final QuestionTagMapper questionTagMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final UserErrorTypeMapper userErrorTypeMapper;
    private final UserAnswerErrorMapper userAnswerErrorMapper;
    private final ReviewService reviewService;
    private final AiReviewTagPromptBuilder reviewTagPromptBuilder;
    private final AiReviewQuestionClient reviewQuestionClient;
    private final ReviewAiResponseValidator reviewAiResponseValidator;

    public UserAnswerServiceImpl(
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            TagMapper tagMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionMapper questionMapper,
            QuestionTagMapper questionTagMapper,
            ErrorTypeMapper errorTypeMapper,
            UserErrorTypeMapper userErrorTypeMapper,
            UserAnswerErrorMapper userAnswerErrorMapper,
            ReviewService reviewService,
            AiReviewTagPromptBuilder reviewTagPromptBuilder,
            AiReviewQuestionClient reviewQuestionClient,
            ReviewAiResponseValidator reviewAiResponseValidator
    ) {
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.tagMapper = tagMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionMapper = questionMapper;
        this.questionTagMapper = questionTagMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.userErrorTypeMapper = userErrorTypeMapper;
        this.userAnswerErrorMapper = userAnswerErrorMapper;
        this.reviewService = reviewService;
        this.reviewTagPromptBuilder = reviewTagPromptBuilder;
        this.reviewQuestionClient = reviewQuestionClient;
        this.reviewAiResponseValidator = reviewAiResponseValidator;
    }

    @Override
    public PageVO<UserAnswerListItemVO> listUserAnswers(UserAnswerQueryRequest request) {
        UserAnswerQueryRequest normalizedRequest = normalizeQueryRequest(request);
        validateScoreRange(normalizedRequest);

        User user = getLocalDefaultUser();
        long total = userAnswerMapper.countUserAnswers(user.getId(), normalizedRequest);
        if (total == 0) {
            return new PageVO<>(List.of(), normalizedRequest.page(), normalizedRequest.size(), 0);
        }

        long offset = (long) (normalizedRequest.page() - 1) * normalizedRequest.size();
        List<UserAnswerListItemVO> items = userAnswerMapper.selectUserAnswerList(
                        user.getId(),
                        normalizedRequest,
                        normalizedRequest.size(),
                        offset
                )
                .stream()
                .map(this::toVO)
                .toList();
        return new PageVO<>(items, normalizedRequest.page(), normalizedRequest.size(), total);
    }

    @Override
    public UserAnswerDetailVO getUserAnswerDetail(Long id) {
        User user = getLocalDefaultUser();
        UserAnswerDetailRow row = userAnswerMapper.selectUserAnswerDetail(user.getId(), id);
        if (row == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "答题记录不存在或已删除");
        }

        List<TagVO> tags = row.getQuestionId() == null
                ? List.of()
                : tagMapper.selectEnabledTagsByQuestionId(row.getQuestionId()).stream().map(this::toTagVO).toList();
        List<QuestionAnswerVO> answers = row.getQuestionId() == null
                ? List.of()
                : questionAnswerMapper.selectActiveAnswersByQuestionId(row.getQuestionId())
                        .stream()
                        .map(this::toQuestionAnswerVO)
                        .toList();
        return toDetailVO(row, tags, answers);
    }

    @Override
    @Transactional
    public List<UserAnswerErrorVO> confirmUserAnswerErrors(
            Long userAnswerId,
            UserAnswerErrorConfirmRequest request
    ) {
        User user = getLocalDefaultUser();
        UserAnswer userAnswer = userAnswerMapper.selectActiveUserAnswerById(user.getId(), userAnswerId);
        if (userAnswer == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作答记录不存在");
        }
        if (!ANSWER_STATUS_REVIEWED.equals(userAnswer.getAnswerStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅已评分的作答可以确认错误");
        }

        LocalDateTime now = LocalDateTime.now();
        if (userAnswer.getQuestionId() == null) {
            List<SavedCorrectionError> savedErrors = request.errors().stream()
                    .map(item -> saveConfirmedCorrectionError(user, userAnswer, item, now))
                    .toList();
            recordCorrectionReviewQuestions(user, userAnswer, savedErrors, now);
            return savedErrors.stream().map(SavedCorrectionError::error).toList();
        }

        Question question = questionMapper.selectQuestionById(userAnswer.getQuestionId());
        if (question == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作答对应题目不存在");
        }

        List<UserAnswerErrorVO> savedErrors = request.errors().stream()
                .map(item -> saveConfirmedError(user, userAnswer, question, item, now))
                .toList();
        if (ARTICLE_QUESTION_TYPE.equals(question.getQuestionType())) {
            recordArticleReviewQuestions(user, userAnswer, question, savedErrors, now);
            return savedErrors;
        }
        savedErrors.stream()
                .map(UserAnswerErrorVO::userErrorTypeId)
                .distinct()
                .sorted()
                .forEach(userErrorTypeId -> reviewService.recordPracticeError(
                        user.getId(), userAnswer.getId(), userAnswer.getQuestionId(), userErrorTypeId, now));
        return savedErrors;
    }

    @Override
    @Transactional
    public ReviewCardCreatedVO createReviewCard(Long userAnswerId, ReviewCardCreateRequest request) {
        User user = getLocalDefaultUser();
        UserAnswer userAnswer = userAnswerMapper.selectActiveUserAnswerById(user.getId(), userAnswerId);
        if (userAnswer == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作答记录不存在");
        }
        if (!ANSWER_STATUS_REVIEWED.equals(userAnswer.getAnswerStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仅已评分的作答可以添加复习卡片");
        }

        String name = request.name().trim();
        String targetExpression = request.targetExpression().trim();
        if (!JAPANESE_KANA_PATTERN.matcher(targetExpression).find()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标日语表达必须包含日语假名");
        }

        Question sourceQuestion = userAnswer.getQuestionId() == null
                ? null
                : questionMapper.selectQuestionById(userAnswer.getQuestionId());
        if (userAnswer.getQuestionId() != null && sourceQuestion == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "作答对应题目不存在");
        }
        String reviewSourceText = resolveCustomReviewSource(sourceQuestion, request);

        ErrorType errorType = errorTypeMapper.selectEnabledLeafByCode(CUSTOM_REVIEW_ERROR_TYPE_CODE);
        if (errorType == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "自定义复习卡片分类不可用");
        }
        UserErrorType existing = userErrorTypeMapper.selectByUserIdAndErrorTypeIdAndName(
                user.getId(), errorType.getId(), name);
        if (existing != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同名复习卡片已存在，请使用其他复习重点");
        }

        LocalDateTime now = LocalDateTime.now();
        UserErrorType userErrorType = new UserErrorType();
        userErrorType.setUserId(user.getId());
        userErrorType.setErrorTypeId(errorType.getId());
        userErrorType.setName(name);
        userErrorType.setDescription(CUSTOM_REVIEW_TYPE_DESCRIPTION);
        userErrorType.setStatus(USER_ERROR_TYPE_STATUS_ACTIVE);
        userErrorType.setCreatedAt(now);
        userErrorType.setUpdatedAt(now);
        userErrorTypeMapper.insertUserErrorType(userErrorType);

        UserAnswerError reviewItem = new UserAnswerError();
        reviewItem.setUserAnswerId(userAnswer.getId());
        reviewItem.setUserId(user.getId());
        reviewItem.setQuestionId(userAnswer.getQuestionId());
        reviewItem.setErrorTypeId(errorType.getId());
        reviewItem.setUserErrorTypeId(userErrorType.getId());
        reviewItem.setOriginalText(reviewSourceText);
        reviewItem.setIssue(name);
        reviewItem.setSuggestion(targetExpression);
        reviewItem.setSeverity("LOW");
        reviewItem.setSortOrder(0);
        reviewItem.setCreatedAt(now);
        reviewItem.setUpdatedAt(now);
        userAnswerErrorMapper.insertUserAnswerError(reviewItem);

        Long reviewQuestionId = createCustomReviewQuestion(
                sourceQuestion, reviewSourceText, targetExpression, name, now);
        ReviewCard card = reviewService.recordPracticeError(
                user.getId(), userAnswer.getId(), reviewQuestionId, userErrorType.getId(), now);
        return new ReviewCardCreatedVO(card.getId(), name, card.getStatus(), card.getDueAt());
    }

    private String resolveCustomReviewSource(Question question, ReviewCardCreateRequest request) {
        if (question == null) {
            if (request.sourceSegmentIndex() != null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "纯日语纠错不能提交中文原句索引");
            }
            String sourceText = requireText(request.reviewSourceText(), "纯日语纠错必须填写复习题中文");
            if (sourceText.length() > 1000
                    || !HAN_PATTERN.matcher(sourceText).find()
                    || JAPANESE_KANA_PATTERN.matcher(sourceText).find()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "复习题中文必须是不超过 1000 字符的中文文本");
            }
            return sourceText;
        }

        if (request.reviewSourceText() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "有题目作答不能提交复习题中文");
        }
        if (ARTICLE_QUESTION_TYPE.equals(question.getQuestionType())) {
            if (request.sourceSegmentIndex() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章复习卡片必须选择中文原句");
            }
            List<String> segments = splitSegments(question.getSourceText());
            int index = request.sourceSegmentIndex();
            if (index >= segments.size()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "sourceSegmentIndex 超出文章原句范围");
            }
            return segments.get(index);
        }
        if (!SHORT_QUESTION_TYPE.equals(question.getQuestionType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前题型不支持自定义复习卡片");
        }
        if (request.sourceSegmentIndex() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "短句复习卡片不能提交 sourceSegmentIndex");
        }
        return requireText(question.getSourceText(), "题目中文原文不能为空");
    }

    private Long createCustomReviewQuestion(
            Question sourceQuestion,
            String sourceText,
            String targetExpression,
            String reviewFocus,
            LocalDateTime now
    ) {
        Question question = new Question();
        question.setQuestionType(SHORT_QUESTION_TYPE);
        question.setSourceText(sourceText);
        question.setContextText(customReviewContext(sourceQuestion));
        question.setLevel(sourceQuestion == null || sourceQuestion.getLevel() == null
                ? DEFAULT_CORRECTION_REVIEW_LEVEL : sourceQuestion.getLevel());
        question.setDifficulty(sourceQuestion == null || sourceQuestion.getDifficulty() == null
                ? DEFAULT_CORRECTION_REVIEW_DIFFICULTY : sourceQuestion.getDifficulty());
        question.setGrammarPoint(reviewFocus);
        question.setSpoken(sourceQuestion != null && Boolean.TRUE.equals(sourceQuestion.getSpoken()));
        question.setBusiness(sourceQuestion != null && Boolean.TRUE.equals(sourceQuestion.getBusiness()));
        question.setExam(sourceQuestion != null && Boolean.TRUE.equals(sourceQuestion.getExam()));
        question.setSourceType(SOURCE_TYPE_REVIEW_DERIVED);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);
        copyEnabledQuestionTags(sourceQuestion, question.getId());

        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(targetExpression);
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        questionAnswerMapper.insertQuestionAnswer(answer);
        return question.getId();
    }

    private String customReviewContext(Question sourceQuestion) {
        if (sourceQuestion == null) {
            return "日语纠错自定义复习";
        }
        String context = sourceQuestion.getContextText();
        return context == null || context.isBlank()
                ? "自定义复习卡片"
                : context.trim() + "（自定义复习卡片）";
    }

    private SavedCorrectionError saveConfirmedCorrectionError(
            User user,
            UserAnswer userAnswer,
            UserAnswerErrorConfirmItemRequest request,
            LocalDateTime now
    ) {
        ResolvedUserErrorType resolvedType = resolveUserErrorType(user.getId(), request, now);
        ErrorType errorType = errorTypeMapper.selectEnabledLeafById(resolvedType.errorTypeId());
        if (errorType == null || TRANSLATION_ONLY_ERROR_CODES.contains(errorType.getCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "该错误类型不适用于纯日语纠错");
        }
        validateCorrectionErrorSource(userAnswer, request);

        UserAnswerError error = new UserAnswerError();
        error.setUserAnswerId(userAnswer.getId());
        error.setUserId(user.getId());
        error.setQuestionId(null);
        error.setErrorTypeId(resolvedType.errorTypeId());
        error.setUserErrorTypeId(resolvedType.userErrorTypeId());
        error.setOriginalText(request.originalText().trim());
        error.setIssue(request.issue().trim());
        error.setSuggestion(request.suggestion().trim());
        error.setSeverity(request.severity());
        error.setSortOrder(request.sortOrder());
        error.setCreatedAt(now);
        error.setUpdatedAt(now);
        userAnswerErrorMapper.insertUserAnswerError(error);
        return new SavedCorrectionError(toUserAnswerErrorVO(error), request.reviewSourceText().trim());
    }

    private void validateCorrectionErrorSource(
            UserAnswer userAnswer,
            UserAnswerErrorConfirmItemRequest request
    ) {
        String original = request.originalText().trim();
        if (!userAnswer.getAnswerText().contains(original)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "originalText 必须属于本次纠错原文");
        }
        String suggestion = request.suggestion().trim();
        if (userAnswer.getAiRevisedText() == null || !userAnswer.getAiRevisedText().contains(suggestion)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "suggestion 必须属于本次完整纠正文稿");
        }
        String reviewSourceText = requireText(
                request.reviewSourceText(),
                "日语纠错错误的 reviewSourceText 不能为空"
        );
        if (reviewSourceText.length() > 1000
                || !HAN_PATTERN.matcher(reviewSourceText).find()
                || JAPANESE_KANA_PATTERN.matcher(reviewSourceText).find()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "reviewSourceText 必须是不超过 1000 字符的中文文本");
        }
    }

    private void recordCorrectionReviewQuestions(
            User user,
            UserAnswer userAnswer,
            List<SavedCorrectionError> savedErrors,
            LocalDateTime now
    ) {
        Map<String, Long> questionIdsByContent = new LinkedHashMap<>();
        Map<Long, List<Long>> questionIdsByUserErrorType = new LinkedHashMap<>();
        for (SavedCorrectionError savedError : savedErrors) {
            UserAnswerErrorVO error = savedError.error();
            String key = savedError.reviewSourceText() + "\u0000" + error.suggestion();
            Long questionId = questionIdsByContent.computeIfAbsent(
                    key,
                    ignored -> createCorrectionReviewQuestion(
                            savedError.reviewSourceText(),
                            error.suggestion(),
                            user.getId(),
                            error.userErrorTypeId(),
                            now
                    )
            );
            questionIdsByUserErrorType
                    .computeIfAbsent(error.userErrorTypeId(), ignored -> new java.util.ArrayList<>())
                    .add(questionId);
        }
        questionIdsByUserErrorType.forEach((userErrorTypeId, questionIds) ->
                reviewService.recordPracticeErrors(
                        user.getId(), userAnswer.getId(), questionIds, userErrorTypeId, now));
    }

    private Long createCorrectionReviewQuestion(
            String sourceText,
            String referenceText,
            Long userId,
            Long userErrorTypeId,
            LocalDateTime now
    ) {
        UserErrorType userErrorType = userErrorTypeMapper.selectActiveByIdAndUserId(userErrorTypeId, userId);
        if (userErrorType == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户错误类型不存在或已归档");
        }

        Question question = new Question();
        question.setQuestionType(SHORT_QUESTION_TYPE);
        question.setSourceText(sourceText);
        question.setContextText("日语纠错错句复习");
        question.setLevel(DEFAULT_CORRECTION_REVIEW_LEVEL);
        question.setDifficulty(DEFAULT_CORRECTION_REVIEW_DIFFICULTY);
        question.setGrammarPoint(userErrorType.getName());
        question.setSpoken(false);
        question.setBusiness(false);
        question.setExam(false);
        question.setSourceType(SOURCE_TYPE_REVIEW_DERIVED);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);

        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(referenceText);
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        questionAnswerMapper.insertQuestionAnswer(answer);
        return question.getId();
    }

    @Override
    public PageVO<UserErrorTypeVO> listUserErrorTypes(UserErrorTypeQueryRequest request) {
        User user = getLocalDefaultUser();
        long total = userErrorTypeMapper.countUserErrorTypes(user.getId(), request.status());
        if (total == 0) {
            return new PageVO<>(List.of(), request.page(), request.size(), 0);
        }

        long offset = (long) (request.page() - 1) * request.size();
        List<UserErrorTypeVO> items = userErrorTypeMapper.selectUserErrorTypeList(
                        user.getId(),
                        request.status(),
                        request.size(),
                        offset
                )
                .stream()
                .map(this::toUserErrorTypeVO)
                .toList();
        return new PageVO<>(items, request.page(), request.size(), total);
    }

    private UserAnswerErrorVO saveConfirmedError(
            User user,
            UserAnswer userAnswer,
            Question question,
            UserAnswerErrorConfirmItemRequest request,
            LocalDateTime now
    ) {
        ResolvedUserErrorType resolvedType = resolveUserErrorType(user.getId(), request, now);
        ErrorType errorType = errorTypeMapper.selectEnabledLeafById(resolvedType.errorTypeId());
        validateConfirmedOriginal(question, userAnswer, errorType, request.originalText());
        UserAnswerError error = new UserAnswerError();
        error.setUserAnswerId(userAnswer.getId());
        error.setUserId(user.getId());
        error.setQuestionId(userAnswer.getQuestionId());
        error.setErrorTypeId(resolvedType.errorTypeId());
        error.setUserErrorTypeId(resolvedType.userErrorTypeId());
        error.setOriginalText(request.originalText().trim());
        error.setIssue(request.issue().trim());
        error.setSuggestion(request.suggestion().trim());
        error.setSeverity(request.severity());
        error.setSortOrder(request.sortOrder());
        error.setCreatedAt(now);
        error.setUpdatedAt(now);
        userAnswerErrorMapper.insertUserAnswerError(error);
        return toUserAnswerErrorVO(error);
    }

    private void validateConfirmedOriginal(
            Question question,
            UserAnswer userAnswer,
            ErrorType errorType,
            String originalText
    ) {
        String original = originalText.trim();
        if (ARTICLE_QUESTION_TYPE.equals(question.getQuestionType()) && "OMISSION".equals(errorType.getCode())) {
            if (!splitSegments(question.getSourceText()).contains(original)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "漏译错误的 originalText 必须是文章中文原句");
            }
            return;
        }
        if (!userAnswer.getAnswerText().contains(original)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "originalText 必须属于本次作答");
        }
    }

    private void recordArticleReviewQuestions(
            User user,
            UserAnswer userAnswer,
            Question articleQuestion,
            List<UserAnswerErrorVO> savedErrors,
            LocalDateTime now
    ) {
        List<QuestionAnswer> answers = questionAnswerMapper.selectActiveAnswersByQuestionId(articleQuestion.getId());
        if (answers.size() != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文章题标准答案不合法");
        }
        List<String> sourceSegments = splitSegments(articleQuestion.getSourceText());
        List<String> referenceSegments = splitSegments(answers.getFirst().getAnswerText());
        if (sourceSegments.size() != referenceSegments.size()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文章题中日段落数不一致");
        }

        Map<String, Long> extractedQuestionIds = new LinkedHashMap<>();
        Map<Long, List<Long>> questionIdsByUserErrorType = new LinkedHashMap<>();
        for (UserAnswerErrorVO error : savedErrors) {
            String referenceText = error.suggestion().trim();
            int segmentIndex = referenceSegments.indexOf(referenceText);
            if (segmentIndex < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文章错误 suggestion 必须是完整日文参考句");
            }
            Long extractedQuestionId = extractedQuestionIds.computeIfAbsent(
                    referenceText,
                    ignored -> createArticleSentenceQuestion(
                            articleQuestion,
                            sourceSegments.get(segmentIndex),
                            referenceText,
                            user.getId(),
                            error.userErrorTypeId(),
                            now
                    )
            );
            questionIdsByUserErrorType
                    .computeIfAbsent(error.userErrorTypeId(), ignored -> new java.util.ArrayList<>())
                    .add(extractedQuestionId);
        }
        questionIdsByUserErrorType.forEach((userErrorTypeId, questionIds) ->
                reviewService.recordPracticeErrors(
                        user.getId(),
                        userAnswer.getId(),
                        questionIds,
                        userErrorTypeId,
                        now
                ));
    }

    private Long createArticleSentenceQuestion(
            Question articleQuestion,
            String sourceText,
            String referenceText,
            Long userId,
            Long userErrorTypeId,
            LocalDateTime now
    ) {
        UserErrorType userErrorType = userErrorTypeMapper.selectActiveByIdAndUserId(
                userErrorTypeId,
                userId
        );
        if (userErrorType == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户错误类型不存在或已归档");
        }
        Question question = new Question();
        question.setQuestionType(SHORT_QUESTION_TYPE);
        question.setSourceText(sourceText);
        question.setContextText(articleQuestion.getContextText() + "（文章错句复习）");
        question.setLevel(articleQuestion.getLevel());
        question.setDifficulty(articleQuestion.getDifficulty());
        question.setGrammarPoint(userErrorType.getName());
        question.setSpoken(articleQuestion.getSpoken());
        question.setBusiness(articleQuestion.getBusiness());
        question.setExam(articleQuestion.getExam());
        question.setSourceType(SOURCE_TYPE_REVIEW_DERIVED);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);
        classifyAndSaveArticleReviewTags(question);

        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(referenceText);
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        questionAnswerMapper.insertQuestionAnswer(answer);
        return question.getId();
    }

    private void copyEnabledQuestionTags(Question sourceQuestion, Long targetQuestionId) {
        if (sourceQuestion == null) {
            return;
        }
        for (Tag tag : tagMapper.selectEnabledTagsByQuestionId(sourceQuestion.getId())) {
            questionTagMapper.insertQuestionTag(targetQuestionId, tag.getId());
        }
    }

    private void classifyAndSaveArticleReviewTags(Question reviewQuestion) {
        List<Tag> sceneTags = tagMapper.selectEnabledTagsByType(TAG_TYPE_SCENE);
        List<Tag> functionTags = tagMapper.selectEnabledTagsByType(TAG_TYPE_FUNCTION);
        Map<String, Tag> allowedTagsByCode = new LinkedHashMap<>();
        sceneTags.forEach(tag -> allowedTagsByCode.put(tag.getCode(), tag));
        functionTags.forEach(tag -> allowedTagsByCode.put(tag.getCode(), tag));

        List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags);
        List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags);
        AiQuestionPrompt prompt = reviewTagPromptBuilder.build(reviewQuestion, sceneTagOptions, functionTagOptions);
        List<String> tagCodes = reviewAiResponseValidator.parseTagCodes(
                reviewQuestionClient.classifyTags(prompt, sceneTagOptions, functionTagOptions),
                sceneTags.stream().map(Tag::getCode).collect(java.util.stream.Collectors.toSet()),
                allowedTagsByCode.keySet());
        for (String tagCode : tagCodes) {
            questionTagMapper.insertQuestionTag(reviewQuestion.getId(), allowedTagsByCode.get(tagCode.trim()).getId());
        }
    }

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(tag.getCode(), tag.getName(), tag.getDescription()))
                .toList();
    }

    private List<String> splitSegments(String text) {
        return List.of(text.replace("\r\n", "\n").replace('\r', '\n').split("\\n\\s*\\n"))
                .stream()
                .map(String::trim)
                .toList();
    }

    private ResolvedUserErrorType resolveUserErrorType(
            Long userId,
            UserAnswerErrorConfirmItemRequest request,
            LocalDateTime now
    ) {
        if (CONFIRM_MODE_EXISTING.equals(request.mode())) {
            if (request.userErrorTypeId() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "追加已有用户错误类型时 userErrorTypeId 不能为空");
            }
            UserErrorType userErrorType = userErrorTypeMapper.selectActiveByIdAndUserId(request.userErrorTypeId(), userId);
            if (userErrorType == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "用户错误类型不存在或已归档");
            }
            validateEnabledLeafErrorType(userErrorType.getErrorTypeId());
            return new ResolvedUserErrorType(userErrorType.getErrorTypeId(), userErrorType.getId());
        }

        if (!CONFIRM_MODE_NEW.equals(request.mode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的错误确认方式");
        }
        if (request.errorTypeId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新建用户错误类型时 errorTypeId 不能为空");
        }
        String name = requireText(request.userErrorTypeName(), "新建用户错误类型时 userErrorTypeName 不能为空");
        String description = requireText(
                request.userErrorTypeDescription(),
                "新建用户错误类型时 userErrorTypeDescription 不能为空"
        );
        validateEnabledLeafErrorType(request.errorTypeId());

        UserErrorType existing = userErrorTypeMapper.selectByUserIdAndErrorTypeIdAndName(
                userId,
                request.errorTypeId(),
                name
        );
        if (existing != null) {
            if (!USER_ERROR_TYPE_STATUS_ACTIVE.equals(existing.getStatus())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "同名用户错误类型已归档，请选择已有类型");
            }
            return new ResolvedUserErrorType(existing.getErrorTypeId(), existing.getId());
        }

        UserErrorType userErrorType = new UserErrorType();
        userErrorType.setUserId(userId);
        userErrorType.setErrorTypeId(request.errorTypeId());
        userErrorType.setName(name);
        userErrorType.setDescription(description);
        userErrorType.setStatus(USER_ERROR_TYPE_STATUS_ACTIVE);
        userErrorType.setCreatedAt(now);
        userErrorType.setUpdatedAt(now);
        userErrorTypeMapper.insertUserErrorType(userErrorType);
        return new ResolvedUserErrorType(userErrorType.getErrorTypeId(), userErrorType.getId());
    }

    private void validateEnabledLeafErrorType(Long errorTypeId) {
        ErrorType errorType = errorTypeMapper.selectEnabledLeafById(errorTypeId);
        if (errorType == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "错误类型不存在、未启用或不是二级分类");
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    private UserAnswerQueryRequest normalizeQueryRequest(UserAnswerQueryRequest request) {
        return new UserAnswerQueryRequest(
                request.answerStatus(),
                request.questionType(),
                request.questionId(),
                request.level(),
                request.minTotalScore(),
                request.maxTotalScore(),
                request.page(),
                request.size()
        );
    }

    private void validateScoreRange(UserAnswerQueryRequest request) {
        if (request.minTotalScore() != null
                && request.maxTotalScore() != null
                && request.minTotalScore().compareTo(request.maxTotalScore()) > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "minTotalScore 不能大于 maxTotalScore");
        }
    }

    private User getLocalDefaultUser() {
        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本地用户不存在或不可用");
        }
        return user;
    }

    private UserAnswerListItemVO toVO(UserAnswerListItemRow row) {
        return new UserAnswerListItemVO(
                row.getId(),
                row.getQuestionId(),
                row.getQuestionType(),
                row.getSourceText(),
                row.getLevel(),
                row.getDifficulty(),
                row.getAnswerText(),
                row.getAnswerStatus(),
                new AnswerScoresVO(
                        row.getGrammarVocabularyScore(),
                        row.getNaturalFluencyScore(),
                        row.getScenarioAdaptationScore(),
                        row.getInformationCompletenessScore()
                ),
                row.getTotalScore(),
                row.getRevisedText(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private UserAnswerDetailVO toDetailVO(
            UserAnswerDetailRow row,
            List<TagVO> tags,
            List<QuestionAnswerVO> answers
    ) {
        return new UserAnswerDetailVO(
                row.getId(),
                row.getQuestionId(),
                row.getQuestionType(),
                row.getSourceText(),
                row.getContextText(),
                row.getLevel(),
                row.getDifficulty(),
                row.getGrammarPoint(),
                tags,
                answers,
                row.getAnswerText(),
                row.getAnswerStatus(),
                new AnswerScoresVO(
                        row.getGrammarVocabularyScore(),
                        row.getNaturalFluencyScore(),
                        row.getScenarioAdaptationScore(),
                        row.getInformationCompletenessScore()
                ),
                row.getTotalScore(),
                row.getOverallComment(),
                row.getRevisedText(),
                row.getCreatedAt(),
                row.getUpdatedAt()
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

    private QuestionAnswerVO toQuestionAnswerVO(QuestionAnswer answer) {
        return new QuestionAnswerVO(
                answer.getId(),
                answer.getAnswerText(),
                answer.getAnswerType(),
                answer.getPrimaryAnswer(),
                answer.getSortOrder()
        );
    }

    private UserAnswerErrorVO toUserAnswerErrorVO(UserAnswerError error) {
        return new UserAnswerErrorVO(
                error.getId(),
                error.getUserAnswerId(),
                error.getErrorTypeId(),
                error.getUserErrorTypeId(),
                error.getOriginalText(),
                error.getIssue(),
                error.getSuggestion(),
                error.getSeverity(),
                error.getSortOrder(),
                error.getCreatedAt()
        );
    }

    private UserErrorTypeVO toUserErrorTypeVO(UserErrorTypeListItemRow row) {
        return new UserErrorTypeVO(
                row.getId(),
                row.getErrorTypeId(),
                row.getErrorTypeCode(),
                row.getErrorTypeName(),
                row.getName(),
                row.getDescription(),
                row.getStatus(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private record ResolvedUserErrorType(Long errorTypeId, Long userErrorTypeId) {
    }

    private record SavedCorrectionError(UserAnswerErrorVO error, String reviewSourceText) {
    }
}
