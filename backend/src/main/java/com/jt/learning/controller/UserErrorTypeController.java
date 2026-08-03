package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.UserErrorTypeQueryRequest;
import com.jt.learning.service.UserAnswerService;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserErrorTypeVO;
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
