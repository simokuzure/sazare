package com.jt.learning.service;

import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.vo.QuestionVO;

import java.util.List;

public interface QuestionService {

    List<QuestionVO> generateQuestionsByAi(AiQuestionGenerationRequest request);
}
