package com.sazare.service.ai.client;

import com.sazare.config.AiClientConfig;
import com.sazare.config.AiProperties;
import com.sazare.dto.ChatMessageDTO;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.prompt.AiChatImportPromptBuilder;
import com.sazare.service.ai.validation.ChatImportAiResponseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiTextClientsTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiClientConfig config = new AiClientConfig();
    private final AiQuestionPrompt prompt = new AiQuestionPrompt("只返回 JSON。", "检查这次操作的原始提示词。");

    @ParameterizedTest
    @ValueSource(strings = {"questions", "article", "answer", "correction", "reviewQuestion", "reviewScore", "chat"})
    void allTextOperationsPreservePromptsAndReturnJson(String operation) {
        AtomicInteger calls = new AtomicInteger();
        AiProviderHttpClient http = (uri, headers, body) -> {
            calls.incrementAndGet();
            assertThat(uri.toString()).isEqualTo("https://api.openai.com/v1/responses");
            JsonNode request = mapper.readTree(body);
            assertThat(request.path("input").get(0).path("role").asString()).isEqualTo("system");
            assertThat(request.path("input").get(0).path("content").asString()).isEqualTo(prompt.systemPrompt());
            assertThat(request.path("input").get(1).path("role").asString()).isEqualTo("user");
            assertThat(request.path("input").get(1).path("content").asString()).isEqualTo(prompt.userPrompt());
            assertThat(request.path("model").asString()).isEqualTo("gpt-6.1-sol");
            return new AiProviderHttpResponse(200, mapper.writeValueAsString(Map.of(
                    "status", "completed", "output", List.of(Map.of(
                            "type", "message", "role", "assistant", "status", "completed",
                            "content", List.of(Map.of("type", "output_text", "text", "{\"result\":true}"))
                    ))
            )));
        };

        assertThat(invoke(operation, http)).isEqualTo("{\"result\":true}");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void chatImportIncludesJsonInstructionInInputWhenChatDataHasNoJsonKeyword() {
        List<ChatMessageDTO> chatMessages = List.of(new ChatMessageDTO(1, "user", "昨日、学校に行きました。"));
        AiQuestionPrompt chatPrompt = new AiChatImportPromptBuilder(mapper).build(
                chatMessages, List.of(), "ZH_TO_JA");
        assertThat(chatPrompt.userPrompt()).doesNotContainIgnoringCase("json");
        String analysisJson = mapper.writeValueAsString(Map.of(
                "messageChecks", List.of(Map.of("messageId", 1, "findings", List.of())), "candidates", List.of()));
        AtomicInteger calls = new AtomicInteger();
        AiProviderHttpClient http = (uri, headers, body) -> {
            calls.incrementAndGet();
            JsonNode messages = mapper.readTree(body).path("input");
            assertThat(messages.isArray()).isTrue();
            assertThat(messages.get(0).path("content").asString()).contains("JSON");
            assertThat(messages.get(1).path("content").asString()).isEqualTo(chatPrompt.userPrompt());
            return new AiProviderHttpResponse(200, mapper.writeValueAsString(Map.of(
                    "status", "completed", "output", List.of(Map.of(
                            "type", "message", "role", "assistant", "status", "completed",
                            "content", List.of(Map.of("type", "output_text", "text", analysisJson))
                    ))
            )));
        };
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.getProviders().getOpenai().setApiKey("test-key");

        String result = config.aiChatImportClient(properties, mapper, http).analyze(chatPrompt);
        assertThat(result).isEqualTo(analysisJson);
        assertThat(new ChatImportAiResponseValidator(mapper).validate(result, chatMessages, Set.of(), "ZH_TO_JA").candidates())
                .isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void chatImportNeverRetriesOrFallsBackOnHttpErrors(int status) {
        AtomicInteger calls = new AtomicInteger();
        AiProviderHttpClient http = (uri, headers, body) -> {
            calls.incrementAndGet();
            return new AiProviderHttpResponse(status, "{}");
        };

        assertThatThrownBy(() -> invoke("chat", http)).isInstanceOf(AiProviderHttpException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"incomplete", "failed"})
    void chatImportRejectsUnfinishedResponseWithoutRetry(String status) {
        AtomicInteger calls = new AtomicInteger();
        AiProviderHttpClient http = (uri, headers, body) -> {
            calls.incrementAndGet();
            return new AiProviderHttpResponse(200, "{\"status\":\"" + status + "\",\"output\":[]}");
        };

        assertThatThrownBy(() -> invoke("chat", http)).hasMessageContaining("未完整完成");
        assertThat(calls.get()).isEqualTo(1);
    }

    private String invoke(String operation, AiProviderHttpClient http) {
        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.getProviders().getOpenai().setApiKey("test-key");
        return switch (operation) {
            case "questions" -> config.aiQuestionClient(properties, mapper, http)
                    .generateQuestions(prompt, null, List.of(), List.of());
            case "article" -> config.aiQuestionClient(properties, mapper, http).generateArticle(prompt, null, "seed");
            case "answer" -> config.aiAnswerScoringClient(properties, mapper, http)
                    .scoreAnswer(prompt, null, null, List.of(), List.of());
            case "correction" -> config.aiJapaneseCorrectionClient(properties, mapper, http).correct(prompt, null);
            case "reviewQuestion" -> config.aiReviewQuestionClient(properties, mapper, http).generateQuestion(prompt);
            case "reviewScore" -> config.aiReviewScoringClient(properties, mapper, http).scoreAnswer(prompt);
            case "chat" -> config.aiChatImportClient(properties, mapper, http).analyze(prompt);
            default -> throw new IllegalArgumentException("未知测试操作: " + operation);
        };
    }
}
