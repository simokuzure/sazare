package com.jt.learning.service.impl;

import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.entity.User;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.vo.AnswerScoresVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserAnswerServiceImpl implements UserAnswerService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";

    private final UserMapper userMapper;
    private final UserAnswerMapper userAnswerMapper;

    public UserAnswerServiceImpl(UserMapper userMapper, UserAnswerMapper userAnswerMapper) {
        this.userMapper = userMapper;
        this.userAnswerMapper = userAnswerMapper;
    }

    @Override
    public PageVO<UserAnswerListItemVO> listUserAnswers(UserAnswerQueryRequest request) {
        UserAnswerQueryRequest normalizedRequest = normalizeQueryRequest(request);
        validateScoreRange(normalizedRequest);

        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本地用户不存在或不可用");
        }

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
                row.getOverallComment(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
