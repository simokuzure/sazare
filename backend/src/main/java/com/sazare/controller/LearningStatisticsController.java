package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.dto.LearningStatisticsQueryRequest;
import com.sazare.service.LearningStatisticsService;
import com.sazare.vo.LearningStatisticsVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LearningStatisticsController {

    private final LearningStatisticsService learningStatisticsService;

    public LearningStatisticsController(LearningStatisticsService learningStatisticsService) {
        this.learningStatisticsService = learningStatisticsService;
    }

    @GetMapping("/learning-statistics")
    public ApiResponse<LearningStatisticsVO> getLearningStatistics(
            @Valid @ModelAttribute LearningStatisticsQueryRequest request
    ) {
        return ApiResponse.success(learningStatisticsService.getLearningStatistics(request));
    }
}
