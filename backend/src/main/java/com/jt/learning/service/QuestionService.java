package com.jt.learning.service;

import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.QuestionCreateRequest;
import com.jt.learning.dto.QuestionEnabledRequest;
import com.jt.learning.dto.QuestionQueryRequest;
import com.jt.learning.dto.QuestionUpdateRequest;
import com.jt.learning.vo.AnswerReviewVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionVO;

import java.util.List;

public interface QuestionService {

    List<QuestionVO> generateQuestionsByAi(AiQuestionGenerationRequest request);

    QuestionVO createQuestion(QuestionCreateRequest request);

    PageVO<QuestionVO> listQuestions(QuestionQueryRequest request);

    QuestionVO getRandomQuestion(QuestionQueryRequest request);

    QuestionVO getQuestion(Long id);

    QuestionVO updateQuestion(Long id, QuestionUpdateRequest request);

    void updateQuestionEnabled(Long id, QuestionEnabledRequest request);

    void deleteQuestion(Long id);

    AnswerReviewVO submitAnswer(Long questionId, AiAnswerScoringRequest request);
}
