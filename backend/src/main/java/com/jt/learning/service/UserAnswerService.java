package com.jt.learning.service;

import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.dto.UserAnswerErrorConfirmRequest;
import com.jt.learning.dto.ReviewCardCreateRequest;
import com.jt.learning.dto.UserErrorTypeQueryRequest;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerErrorVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import com.jt.learning.vo.ReviewCardCreatedVO;
import com.jt.learning.vo.UserErrorTypeVO;

import java.util.List;

public interface UserAnswerService {

    PageVO<UserAnswerListItemVO> listUserAnswers(UserAnswerQueryRequest request);

    UserAnswerDetailVO getUserAnswerDetail(Long id);

    List<UserAnswerErrorVO> confirmUserAnswerErrors(Long userAnswerId, UserAnswerErrorConfirmRequest request);

    ReviewCardCreatedVO createReviewCard(Long userAnswerId, ReviewCardCreateRequest request);

    PageVO<UserErrorTypeVO> listUserErrorTypes(UserErrorTypeQueryRequest request);
}
