package com.jt.learning.service.impl;

import com.jt.learning.dto.UserAnswerDetailRow;
import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.TagVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserAnswerServiceImpl implements UserAnswerService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";

    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;
    private final TagMapper tagMapper;
    private final QuestionAnswerMapper questionAnswerMapper;

    public UserAnswerServiceImpl(
            UserMapper userMapper,
            UserAnswerMapper userAnswerMapper,
            TagMapper tagMapper,
            QuestionAnswerMapper questionAnswerMapper
    ) {
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
        this.tagMapper = tagMapper;
        this.questionAnswerMapper = questionAnswerMapper;
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
}
