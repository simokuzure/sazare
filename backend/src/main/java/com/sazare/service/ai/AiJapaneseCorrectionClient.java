package com.sazare.service.ai;

import com.sazare.dto.JapaneseCorrectionRequest;

public interface AiJapaneseCorrectionClient {

    String correct(AiQuestionPrompt prompt, JapaneseCorrectionRequest request);
}
