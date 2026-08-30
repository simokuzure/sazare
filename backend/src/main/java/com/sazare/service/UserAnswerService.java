package com.sazare.service;

import com.sazare.dto.UserAnswerQueryRequest;
import com.sazare.dto.UserAnswerErrorConfirmRequest;
import com.sazare.dto.ReviewCardCreateRequest;
import com.sazare.dto.UserErrorTypeQueryRequest;
import com.sazare.vo.PageVO;
import com.sazare.vo.UserAnswerErrorVO;
import com.sazare.vo.UserAnswerDetailVO;
import com.sazare.vo.UserAnswerListItemVO;
import com.sazare.vo.ReviewCardCreatedVO;
import com.sazare.vo.UserErrorTypeVO;

import java.util.List;

public interface UserAnswerService {

    PageVO<UserAnswerListItemVO> listUserAnswers(UserAnswerQueryRequest request);

    UserAnswerDetailVO getUserAnswerDetail(Long id);

    List<UserAnswerErrorVO> confirmUserAnswerErrors(Long userAnswerId, UserAnswerErrorConfirmRequest request);

    ReviewCardCreatedVO createReviewCard(Long userAnswerId, ReviewCardCreateRequest request);

    PageVO<UserErrorTypeVO> listUserErrorTypes(UserErrorTypeQueryRequest request);
}
