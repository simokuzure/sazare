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
import java.util.Map;

public class GoogleAiAnswerScoringClient implements AiAnswerScoringClient {

    private final GoogleGenerateContentClient generateContentClient;

    public GoogleAiAnswerScoringClient(
            AiProperties.Google properties,
            ObjectMapper objectMapper,
            AiProviderHttpClient httpClient
    ) {
        this.generateContentClient = new GoogleGenerateContentClient(properties, objectMapper, httpClient);
    }

    @Override
    public String scoreAnswer(
            AiQuestionPrompt prompt,
            AiAnswerScoringRequest request,
            Question question,
            List<QuestionAnswer> standardAnswers,
            List<AiQuestionTagOptionDTO> tagOptions
    ) {
        return generateContentClient.generate(
                prompt,
                Map.of("responseMimeType", GoogleGenerateContentClient.JSON_CONTENT_TYPE)
        );
    }
}
