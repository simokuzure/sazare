package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.service.QuestionService;
import com.jt.learning.vo.AnswerReviewVO;
import com.jt.learning.vo.QuestionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping("/questions")
@Validated
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

    @PostMapping("/{questionId}/answers")
    public ApiResponse<AnswerReviewVO> submitAnswer(
            @Positive(message = "questionId 必须大于 0") @PathVariable Long questionId,
            @Valid @RequestBody AiAnswerScoringRequest request
    ) {
        return ApiResponse.success(questionService.submitAnswer(questionId, request));
    }
}
