package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.service.QuestionService;
import com.jt.learning.vo.QuestionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @PostMapping("/ai-generations")
    public ApiResponse<List<QuestionVO>> generateQuestionsByAi(
            @Valid @RequestBody AiQuestionGenerationRequest request
    ) {
        return ApiResponse.success(questionService.generateQuestionsByAi(request));
    }
}