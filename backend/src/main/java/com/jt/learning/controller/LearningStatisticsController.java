package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.LearningStatisticsQueryRequest;
import com.jt.learning.service.LearningStatisticsService;
import com.jt.learning.vo.LearningStatisticsVO;
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
