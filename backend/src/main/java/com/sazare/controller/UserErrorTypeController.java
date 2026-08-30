package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.dto.UserErrorTypeQueryRequest;
import com.sazare.service.UserAnswerService;
import com.sazare.vo.PageVO;
import com.sazare.vo.UserErrorTypeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user-error-types")
public class UserErrorTypeController {

    private final UserAnswerService userAnswerService;

    public UserErrorTypeController(UserAnswerService userAnswerService) {
        this.userAnswerService = userAnswerService;
    }

    @GetMapping
    public ApiResponse<PageVO<UserErrorTypeVO>> listUserErrorTypes(
            @Valid @ModelAttribute UserErrorTypeQueryRequest request
    ) {
        return ApiResponse.success(userAnswerService.listUserErrorTypes(request));
    }
}
