package com.jt.learning.service;

import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerListItemVO;

public interface UserAnswerService {

    PageVO<UserAnswerListItemVO> listUserAnswers(UserAnswerQueryRequest request);

    UserAnswerDetailVO getUserAnswerDetail(Long id);
}
