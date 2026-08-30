package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.service.JapaneseCorrectionService;
import com.sazare.vo.JapaneseCorrectionReviewVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JapaneseCorrectionController {

    private final JapaneseCorrectionService japaneseCorrectionService;

    public JapaneseCorrectionController(JapaneseCorrectionService japaneseCorrectionService) {
        this.japaneseCorrectionService = japaneseCorrectionService;
    }

    @PostMapping("/japanese-corrections")
    public ApiResponse<JapaneseCorrectionReviewVO> correct(
            @Valid @RequestBody JapaneseCorrectionRequest request
    ) {
        return ApiResponse.success(japaneseCorrectionService.correct(request));
    }
}
