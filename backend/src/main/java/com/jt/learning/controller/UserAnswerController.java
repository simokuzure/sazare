package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.dto.UserAnswerErrorConfirmRequest;
import com.jt.learning.dto.ReviewCardCreateRequest;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerErrorVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import com.jt.learning.vo.ReviewCardCreatedVO;
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
