package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.ErrorTypeQueryRequest;
import com.jt.learning.service.ErrorTypeService;
import com.jt.learning.vo.ErrorTypeVO;
import com.jt.learning.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/error-types")
public class ErrorTypeController {

    private final ErrorTypeService errorTypeService;

    public ErrorTypeController(ErrorTypeService errorTypeService) {
        this.errorTypeService = errorTypeService;
    }

    @GetMapping
    public ApiResponse<PageVO<ErrorTypeVO>> listErrorTypes(
            @Valid @ModelAttribute ErrorTypeQueryRequest request
    ) {
        return ApiResponse.success(errorTypeService.listErrorTypes(request));
    }
}
