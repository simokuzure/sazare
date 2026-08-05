package com.jt.learning.service;

import com.jt.learning.dto.ReviewAttemptRequest;
import com.jt.learning.dto.ReviewCardQueryRequest;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.ReviewAttemptVO;
import com.jt.learning.vo.ReviewCardDetailVO;
import com.jt.learning.vo.ReviewCardListVO;
import com.jt.learning.vo.ReviewDerivedQuestionGenerationVO;

import java.time.LocalDateTime;

public interface ReviewService {

    PageVO<ReviewCardListVO> listReviewCards(ReviewCardQueryRequest request);

    ReviewCardDetailVO getReviewCard(Long cardId);

    ReviewAttemptVO submitReviewAttempt(Long cardId, ReviewAttemptRequest request);

    ReviewDerivedQuestionGenerationVO generateDerivedQuestion(Long cardId);

    void recordPracticeError(
            Long userId,
            Long userAnswerId,
            Long questionId,
            Long userErrorTypeId,
            LocalDateTime occurredAt
    );
}
