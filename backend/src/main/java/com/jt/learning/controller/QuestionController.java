package com.jt.learning.controller;

import com.jt.learning.common.ApiResponse;
import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.QuestionCreateRequest;
import com.jt.learning.dto.QuestionEnabledRequest;
import com.jt.learning.dto.QuestionEmbeddingBackfillRequest;
import com.jt.learning.dto.QuestionQueryRequest;
import com.jt.learning.dto.QuestionUpdateRequest;
import com.jt.learning.service.QuestionService;
import com.jt.learning.vo.AnswerReviewVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionVO;
import com.jt.learning.vo.QuestionEmbeddingBackfillVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/article-ai-generations")
    public ApiResponse<QuestionVO> generateArticleByAi(
            @Valid @RequestBody AiArticleGenerationRequest request
    ) {
        return ApiResponse.success(questionService.generateArticleByAi(request));
    }

    @PostMapping("/embedding-backfills")
    public ApiResponse<QuestionEmbeddingBackfillVO> backfillQuestionEmbeddings(
            @Valid @RequestBody(required = false) QuestionEmbeddingBackfillRequest request
    ) {
        QuestionEmbeddingBackfillRequest normalizedRequest = request == null
                ? new QuestionEmbeddingBackfillRequest(null)
                : request;
        return ApiResponse.success(questionService.backfillQuestionEmbeddings(normalizedRequest));
    }

    @PostMapping
    public ApiResponse<QuestionVO> createQuestion(@Valid @RequestBody QuestionCreateRequest request) {
        return ApiResponse.success(questionService.createQuestion(request));
    }

    @GetMapping
    public ApiResponse<PageVO<QuestionVO>> listQuestions(
            @Pattern(regexp = "TRANSLATION_ZH_TO_JA|TRANSLATION_ZH_TO_JA_ARTICLE",
                    message = "questionType 只能是 TRANSLATION_ZH_TO_JA 或 TRANSLATION_ZH_TO_JA_ARTICLE")
            @RequestParam(defaultValue = "TRANSLATION_ZH_TO_JA") String questionType,
            @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
            @RequestParam(required = false) String level,
            @Min(value = 1, message = "difficulty 必须在 1 到 5 之间")
            @Max(value = 5, message = "difficulty 必须在 1 到 5 之间")
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) List<String> tagCodes,
            @RequestParam(required = false) Boolean spoken,
            @RequestParam(required = false) Boolean business,
            @RequestParam(required = false) Boolean exam,
            @Pattern(regexp = "AI|MANUAL|REVIEW_DERIVED", message = "sourceType 只能是 AI、MANUAL 或 REVIEW_DERIVED")
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "true") Boolean enabled,
            @Min(value = 1, message = "page 必须大于等于 1")
            @RequestParam(defaultValue = "1") Integer page,
            @Min(value = 1, message = "size 必须在 1 到 100 之间")
            @Max(value = 100, message = "size 必须在 1 到 100 之间")
            @RequestParam(defaultValue = "20") Integer size
    ) {
        QuestionQueryRequest request = new QuestionQueryRequest(
                questionType,
                level,
                difficulty,
                tagCodes,
                spoken,
                business,
                exam,
                sourceType,
                enabled,
                page,
                size
        );
        return ApiResponse.success(questionService.listQuestions(request));
    }

    @GetMapping("/random")
    public ApiResponse<QuestionVO> getRandomQuestion(
            @Pattern(regexp = "TRANSLATION_ZH_TO_JA|TRANSLATION_ZH_TO_JA_ARTICLE",
                    message = "questionType 只能是 TRANSLATION_ZH_TO_JA 或 TRANSLATION_ZH_TO_JA_ARTICLE")
            @RequestParam(defaultValue = "TRANSLATION_ZH_TO_JA") String questionType,
            @Pattern(regexp = "N5|N4|N3|N2|N1", message = "level 只能是 N5、N4、N3、N2、N1")
            @RequestParam(required = false) String level,
            @Min(value = 1, message = "difficulty 必须在 1 到 5 之间")
            @Max(value = 5, message = "difficulty 必须在 1 到 5 之间")
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) List<String> tagCodes,
            @RequestParam(required = false) Boolean spoken,
            @RequestParam(required = false) Boolean business,
            @RequestParam(required = false) Boolean exam,
            @Pattern(regexp = "AI|MANUAL", message = "sourceType 只能是 AI 或 MANUAL")
            @RequestParam(required = false) String sourceType,
            @RequestParam(defaultValue = "true") Boolean enabled
    ) {
        QuestionQueryRequest request = new QuestionQueryRequest(
                questionType,
                level,
                difficulty,
                tagCodes,
                spoken,
                business,
                exam,
                sourceType,
                enabled,
                1,
                1
        );
        return ApiResponse.success(questionService.getRandomQuestion(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionVO> getQuestion(
            @Positive(message = "id 必须大于 0") @PathVariable Long id
    ) {
        return ApiResponse.success(questionService.getQuestion(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionVO> updateQuestion(
            @Positive(message = "id 必须大于 0") @PathVariable Long id,
            @Valid @RequestBody QuestionUpdateRequest request
    ) {
        return ApiResponse.success(questionService.updateQuestion(id, request));
    }

    @PatchMapping("/{id}/enabled")
    public ApiResponse<Void> updateQuestionEnabled(
            @Positive(message = "id 必须大于 0") @PathVariable Long id,
            @Valid @RequestBody QuestionEnabledRequest request
    ) {
        questionService.updateQuestionEnabled(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteQuestion(
            @Positive(message = "id 必须大于 0") @PathVariable Long id
    ) {
        questionService.deleteQuestion(id);
        return ApiResponse.success();
    }

    @PostMapping("/{questionId}/answers")
    public ApiResponse<AnswerReviewVO> submitAnswer(
            @Positive(message = "questionId 必须大于 0") @PathVariable Long questionId,
            @Valid @RequestBody AiAnswerScoringRequest request
    ) {
        return ApiResponse.success(questionService.submitAnswer(questionId, request));
    }
}
