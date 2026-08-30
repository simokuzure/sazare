package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.dto.ErrorTypeQueryRequest;
import com.sazare.service.ErrorTypeService;
import com.sazare.vo.ErrorTypeVO;
import com.sazare.vo.PageVO;
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
