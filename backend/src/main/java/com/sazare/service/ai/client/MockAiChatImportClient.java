package com.sazare.service.ai.client;

import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.AiQuestionPrompt;
import tools.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Map;

/** Mock 仅验证完整消息覆盖和接口流程，不模拟真实错误识别。 */
public class MockAiChatImportClient implements AiChatImportClient {
    private final ObjectMapper mapper;

    public MockAiChatImportClient(ObjectMapper mapper) { this.mapper = mapper; }

    @Override
    public String analyze(AiQuestionPrompt prompt) {
        var ids = new ArrayList<Integer>();
        for (var message : mapper.readTree(prompt.userPrompt()).get("messages")) {
            if ("user".equals(message.get("role").asString())) ids.add(message.get("id").asInt());
        }
        return mapper.writeValueAsString(Map.of("checkedUserMessageIds", ids, "candidates", java.util.List.of()));
    }
}
