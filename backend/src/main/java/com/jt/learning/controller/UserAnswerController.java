package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

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
}
