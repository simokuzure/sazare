package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.dto.UserAnswerQueryRequest;
import com.sazare.dto.UserAnswerErrorConfirmRequest;
import com.sazare.dto.ReviewCardCreateRequest;
import com.sazare.service.UserAnswerService;
import com.sazare.vo.PageVO;
import com.sazare.vo.UserAnswerErrorVO;
import com.sazare.vo.UserAnswerDetailVO;
import com.sazare.vo.UserAnswerListItemVO;
import com.sazare.vo.ReviewCardCreatedVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserAnswerController {

    private final UserAnswerService userAnswerService;

    public UserAnswerController(UserAnswerService userAnswerService) {
        this.userAnswerService = userAnswerService;
    }

    @GetMapping("/user-answers")
    public ApiResponse<PageVO<UserAnswerListItemVO>> listUserAnswers(
            @Valid @ModelAttribute UserAnswerQueryRequest request
    ) {
        return ApiResponse.success(userAnswerService.listUserAnswers(request));
    }

    @GetMapping("/user-answers/{id}")
    public ApiResponse<UserAnswerDetailVO> getUserAnswerDetail(@PathVariable Long id) {
        return ApiResponse.success(userAnswerService.getUserAnswerDetail(id));
    }

    @PostMapping("/user-answers/{id}/errors")
    public ApiResponse<List<UserAnswerErrorVO>> confirmUserAnswerErrors(
            @PathVariable Long id,
            @Valid @RequestBody UserAnswerErrorConfirmRequest request
    ) {
        return ApiResponse.success(userAnswerService.confirmUserAnswerErrors(id, request));
    }

    @PostMapping("/user-answers/{id}/review-cards")
    public ApiResponse<ReviewCardCreatedVO> createReviewCard(
            @PathVariable Long id,
            @Valid @RequestBody ReviewCardCreateRequest request
    ) {
        return ApiResponse.success(userAnswerService.createReviewCard(id, request));
    }
}
