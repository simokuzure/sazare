package com.sazare.service;

import com.sazare.dto.ReviewAttemptRequest;
import com.sazare.dto.ReviewCardQueryRequest;
import com.sazare.entity.ReviewCard;
import com.sazare.vo.PageVO;
import com.sazare.vo.ReviewAttemptVO;
import com.sazare.vo.ReviewCardDetailVO;
import com.sazare.vo.ReviewCardListVO;
import com.sazare.vo.ReviewDerivedQuestionGenerationVO;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewService {

    PageVO<ReviewCardListVO> listReviewCards(ReviewCardQueryRequest request);

    ReviewCardDetailVO getReviewCard(Long cardId, boolean earlyReview);

    void deleteReviewCard(Long cardId);

    ReviewAttemptVO submitReviewAttempt(Long cardId, ReviewAttemptRequest request);

    ReviewDerivedQuestionGenerationVO generateDerivedQuestion(Long cardId);

    ReviewCard recordPracticeError(
            Long userId,
            Long userAnswerId,
            Long questionId,
            Long userErrorTypeId,
            LocalDateTime occurredAt
    );

    void recordPracticeErrors(
            Long userId,
            Long userAnswerId,
            List<Long> questionIds,
            Long userErrorTypeId,
            LocalDateTime occurredAt
    );
}
