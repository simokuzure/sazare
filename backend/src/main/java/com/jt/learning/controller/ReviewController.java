package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.ReviewAttemptRequest;
import com.jt.learning.dto.ReviewCardQueryRequest;
import com.jt.learning.service.ReviewService;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.ReviewAttemptVO;
import com.jt.learning.vo.ReviewCardDetailVO;
import com.jt.learning.vo.ReviewCardListVO;
import com.jt.learning.vo.ReviewDerivedQuestionGenerationVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/review-cards")
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ApiResponse<PageVO<ReviewCardListVO>> listReviewCards(
            @Valid @ModelAttribute ReviewCardQueryRequest request
    ) {
        return ApiResponse.success(reviewService.listReviewCards(request));
    }

    @GetMapping("/{cardId}")
    public ApiResponse<ReviewCardDetailVO> getReviewCard(
            @Positive(message = "cardId 必须大于 0") @PathVariable Long cardId,
            @RequestParam(defaultValue = "false") boolean earlyReview
    ) {
        return ApiResponse.success(reviewService.getReviewCard(cardId, earlyReview));
    }

    @DeleteMapping("/{cardId}")
    public ApiResponse<Void> deleteReviewCard(
            @Positive(message = "cardId 必须大于 0") @PathVariable Long cardId
    ) {
        reviewService.deleteReviewCard(cardId);
        return ApiResponse.success();
    }

    @PostMapping("/{cardId}/attempts")
    public ApiResponse<ReviewAttemptVO> submitReviewAttempt(
            @Positive(message = "cardId 必须大于 0") @PathVariable Long cardId,
            @Valid @RequestBody ReviewAttemptRequest request
    ) {
        return ApiResponse.success(reviewService.submitReviewAttempt(cardId, request));
    }

    @PostMapping("/{cardId}/derived-question-generations")
    public ApiResponse<ReviewDerivedQuestionGenerationVO> generateDerivedQuestion(
            @Positive(message = "cardId 必须大于 0") @PathVariable Long cardId
    ) {
        return ApiResponse.success(reviewService.generateDerivedQuestion(cardId));
    }
}
