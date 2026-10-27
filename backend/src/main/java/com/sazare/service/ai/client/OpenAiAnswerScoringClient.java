package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.dto.AiAnswerScoringRequest;
import com.sazare.dto.AiQuestionTagOptionDTO;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.service.ai.AiAnswerScoringClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

public class OpenAiAnswerScoringClient implements AiAnswerScoringClient {

    private final OpenAiResponsesClient client;

    public OpenAiAnswerScoringClient(AiProperties.OpenAi properties, ObjectMapper mapper, AiProviderHttpClient http) {
        client = new OpenAiResponsesClient(properties, mapper, http);
    }

    @Override
    public String scoreAnswer(
            AiQuestionPrompt prompt,
            AiAnswerScoringRequest request,
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions
    ) {
        return client.generate(prompt);
    }
}
