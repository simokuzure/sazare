package com.jt.learning.service.impl;

import com.jt.learning.dto.UserAnswerDetailRow;
import com.jt.learning.dto.UserAnswerErrorConfirmItemRequest;
import com.jt.learning.dto.UserAnswerErrorConfirmRequest;
import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.dto.UserErrorTypeListItemRow;
import com.jt.learning.dto.UserErrorTypeQueryRequest;
import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.entity.UserAnswerError;
import com.jt.learning.entity.UserErrorType;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerErrorMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserErrorTypeMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.TagVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerErrorVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import com.jt.learning.vo.UserErrorTypeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserAnswerServiceImpl implements UserAnswerService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String ANSWER_STATUS_REVIEWED = "REVIEWED";
    private static final String USER_ERROR_TYPE_STATUS_ACTIVE = "ACTIVE";
    private static final String CONFIRM_MODE_NEW = "NEW_USER_ERROR_TYPE";
    private static final String CONFIRM_MODE_EXISTING = "EXISTING_USER_ERROR_TYPE";

    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final TagMapper tagMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final ErrorTypeMapper errorTypeMapper;
    private final UserErrorTypeMapper userErrorTypeMapper;
    private final UserAnswerErrorMapper userAnswerErrorMapper;

    public UserAnswerServiceImpl(
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            TagMapper tagMapper,
            QuestionAnswerMapper questionAnswerMapper,
            ErrorTypeMapper errorTypeMapper,
            UserErrorTypeMapper userErrorTypeMapper,
            UserAnswerErrorMapper userAnswerErrorMapper
    ) {
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.tagMapper = tagMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.errorTypeMapper = errorTypeMapper;
        this.userErrorTypeMapper = userErrorTypeMapper;
        this.userAnswerErrorMapper = userAnswerErrorMapper;
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

        List<TagVO> tags = tagMapper.selectEnabledTagsByQuestionId(row.getQuestionId())
                .stream()
                .map(this::toTagVO)
                .toList();
        List<QuestionAnswerVO> answers = questionAnswerMapper.selectActiveAnswersByQuestionId(row.getQuestionId())
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
        return request.errors().stream()
                .map(item -> saveConfirmedError(user, userAnswer, item, now))
                .toList();
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
            UserAnswerErrorConfirmItemRequest request,
            LocalDateTime now
    ) {
        if (!userAnswer.getAnswerText().contains(request.originalText().trim())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "originalText 必须属于本次作答");
        }

        ResolvedUserErrorType resolvedType = resolveUserErrorType(user.getId(), request, now);
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
}
