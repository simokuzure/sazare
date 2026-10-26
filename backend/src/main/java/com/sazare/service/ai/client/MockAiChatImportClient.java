package com.sazare.service.ai.client;

import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.dto.AiChatImportAnalysisDTO;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Mock 仅验证完整消息覆盖和接口流程，不模拟真实错误识别。 */
public class MockAiChatImportClient implements AiChatImportClient {
    private final ObjectMapper mapper;

    public MockAiChatImportClient(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public String analyze(AiQuestionPrompt prompt) {
        var checks = new ArrayList<AiChatImportAnalysisDTO.MessageCheck>();
        for (var message : mapper.readTree(prompt.userPrompt()).get("messages")) {
            if ("user".equals(message.get("role").asString())) {
                checks.add(new AiChatImportAnalysisDTO.MessageCheck(message.get("id").asInt(), List.of()));
            }
        }
        return mapper.writeValueAsString(new AiChatImportAnalysisDTO(checks, List.of()));
    }
}
