package com.jt.learning.service.ai;

import com.jt.learning.dto.JapaneseCorrectionRequest;

public interface AiJapaneseCorrectionClient {

    String correct(AiQuestionPrompt prompt, JapaneseCorrectionRequest request);
}
