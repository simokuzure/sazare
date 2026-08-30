package com.sazare.service;

import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiAnswerScoringRequest;
import com.sazare.dto.QuestionCreateRequest;
import com.sazare.dto.QuestionEnabledRequest;
import com.sazare.dto.QuestionEmbeddingBackfillRequest;
import com.sazare.dto.QuestionQueryRequest;
import com.sazare.dto.QuestionUpdateRequest;
import com.sazare.vo.AnswerReviewVO;
import com.sazare.vo.PageVO;
import com.sazare.vo.QuestionVO;
import com.sazare.vo.QuestionEmbeddingBackfillVO;

import java.util.List;

public interface QuestionService {

    List<QuestionVO> generateQuestionsByAi(AiQuestionGenerationRequest request);

    QuestionVO generateArticleByAi(AiArticleGenerationRequest request);

    QuestionEmbeddingBackfillVO backfillQuestionEmbeddings(QuestionEmbeddingBackfillRequest request);

    QuestionVO createQuestion(QuestionCreateRequest request);

    PageVO<QuestionVO> listQuestions(QuestionQueryRequest request);

    QuestionVO getRandomQuestion(QuestionQueryRequest request);

    QuestionVO getQuestion(Long id);

    QuestionVO updateQuestion(Long id, QuestionUpdateRequest request);

    void updateQuestionEnabled(Long id, QuestionEnabledRequest request);

    void deleteQuestion(Long id);

    AnswerReviewVO submitAnswer(Long questionId, AiAnswerScoringRequest request);
}
