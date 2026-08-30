package com.sazare.service.ai;

import com.sazare.dto.AiAnswerScoringRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;

import java.util.List;

public interface AiAnswerScoringClient {

    String scoreAnswer(
            AiQuestionPrompt prompt,
            AiAnswerScoringRequest request,
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions
    );
}
