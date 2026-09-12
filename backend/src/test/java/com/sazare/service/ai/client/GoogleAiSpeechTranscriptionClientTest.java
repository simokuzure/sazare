package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.impl.SpeechTranscriptionServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoogleAiSpeechTranscriptionClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiProviderHttpClient http = mock(AiProviderHttpClient.class);

    @Test
    void shouldSendStatelessInlineAudioWithJapaneseVerbatimConfiguration() {
        when(http.postJson(any(), anyMap(), anyString())).thenReturn(response("日本語"));
        String result = client(properties()).transcribe(new byte[]{1, 2, 3}, "audio/webm");
        assertThat(mapper.readTree(result).path("text").asString()).isEqualTo("日本語");
        var body = captureRequest();
        assertThat(body.path("model").asString()).isEqualTo("gemini-3.5-transcribe");
        assertThat(body.path("store").isBoolean()).isTrue();
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("stream").isBoolean()).isTrue();
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("input").size()).isEqualTo(1);
        var audio = body.path("input").get(0);
        assertThat(audio.path("type").asString()).isEqualTo("audio");
        assertThat(audio.path("mime_type").asString()).isEqualTo("audio/webm");
        assertThat(Base64.getDecoder().decode(audio.path("data").asString())).containsExactly(1, 2, 3);
        assertThat(audio.has("uri")).isFalse();
        var config = body.path("generation_config").path("transcription_config");
        assertThat(config.path("language_codes").get(0).asString()).isEqualTo("ja-JP");
        assertThat(config.path("mode").path("type").asString()).isEqualTo("verbatim");
        assertThat(body.toString()).doesNotContain("system_instruction", "systemInstruction", "temperature",
                "responseSchema", "response_format", "previous_interaction_id", "inlineData", "smart");
    }

    @Test
    void shouldUseDedicatedOverrideInsteadOfGenerationModel() {
        var properties = properties();
        properties.setModel("unrelated-generation-model");
        properties.setTranscriptionModel(" models/transcription-model ");
        properties.setBaseUrl(" https://example.com/v1beta/// ");
        when(http.postJson(any(), anyMap(), anyString())).thenReturn(response("日本語"));
        client(properties).transcribe(new byte[]{1}, "audio/ogg");
        var request = captureRequest();
        assertThat(request.path("model").asString()).isEqualTo("transcription-model");
        assertThat(request.path("input").get(0).path("mime_type").asString()).isEqualTo("audio/ogg");
    }

    @Test
    void shouldJoinOnlyModelTextAndPreserveLiteralQuotesAndNewlines() {
        var response = Map.of("status", "completed", "steps", List.of(
                Map.of("type", "user_input", "content", List.of(Map.of("type", "text", "text", "not-output"))),
                Map.of("type", "model_output", "content", List.of(
                        Map.of("type", "text", "text", "今日は ", "annotations", List.of()),
                        Map.of("type", "text", "text", "\"日本語\"\nを"))),
                Map.of("type", "model_output", "content", List.of(Map.of("type", "text", "text", "勉強します。")))));
        when(http.postJson(any(), anyMap(), anyString()))
                .thenReturn(new AiProviderHttpResponse(200, mapper.writeValueAsString(response)));
        String transcript = new SpeechTranscriptionServiceImpl(client(properties()), mapper)
                .transcribe(new byte[]{1}, "audio/webm").text();
        assertThat(transcript).isEqualTo("今日は \"日本語\"\nを勉強します。");
    }

    @Test
    void shouldRejectIncompleteOrMalformedResponsesWithoutExposingContent() {
        for (String body : new String[]{null, "", "private-transcript", "null", "{}",
                "{\"status\":\"in_progress\",\"steps\":[]}",
                "{\"status\":\"failed\",\"error\":{\"message\":\"private-transcript\"}}",
                "{\"status\":\"completed\"}",
                "{\"status\":\"completed\",\"steps\":[null]}",
                "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":{}}]}",
                "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"text\",\"text\":12}]}]}",
                "{\"status\":\"completed\",\"steps\":[{\"type\":\"model_output\",\"content\":[{\"type\":\"refusal\",\"text\":\"private-transcript\"}]}]}"}) {
            reset(http);
            when(http.postJson(any(), anyMap(), anyString())).thenReturn(new AiProviderHttpResponse(200, body));
            assertThatThrownBy(() -> client(properties()).transcribe(new byte[]{1}, "audio/webm"))
                    .isInstanceOf(BusinessException.class).hasNoCause()
                    .hasMessageContaining("Google AI 语音转写")
                    .hasMessageNotContaining("private-transcript");
            verify(http, times(1)).postJson(any(), anyMap(), anyString());
        }
    }

    @Test
    void shouldTreatEmptyCompletedTranscriptAsNoSpeech() {
        for (AiProviderHttpResponse response : List.of(response(" "),
                new AiProviderHttpResponse(200, "{\"status\":\"completed\",\"steps\":[]}"))) {
            when(http.postJson(any(), anyMap(), anyString())).thenReturn(response);
            assertThatThrownBy(() -> new SpeechTranscriptionServiceImpl(client(properties()), mapper)
                    .transcribe(new byte[]{1}, "audio/webm"))
                    .hasMessage("未识别到语音，请重新录音");
        }
    }

    @Test
    void shouldNotExposeProviderErrorBodyOrRetry() {
        for (int status : new int[]{401, 429, 500}) {
            reset(http);
            when(http.postJson(any(), anyMap(), anyString()))
                    .thenReturn(new AiProviderHttpResponse(status, "private-transcript"));
            assertThatThrownBy(() -> client(properties()).transcribe(new byte[]{1}, "audio/webm"))
                    .isInstanceOfSatisfying(AiProviderHttpException.class,
                            exception -> assertThat(exception.getStatusCode()).isEqualTo(status))
                    .hasMessage("Google AI 语音转写服务异常");
            verify(http, times(1)).postJson(any(), anyMap(), anyString());
        }
    }

    @Test
    void shouldValidateConfigurationBeforeSendingAudio() {
        for (String field : List.of("key", "model", "baseUrl")) {
            var properties = properties();
            if (field.equals("key")) properties.setApiKey("");
            if (field.equals("model")) properties.setTranscriptionModel("");
            if (field.equals("baseUrl")) properties.setBaseUrl("");
            assertThatThrownBy(() -> client(properties).transcribe(new byte[]{1}, "audio/webm"))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("未配置");
        }
        verifyNoInteractions(http);
    }

    @Test
    void shouldPreserveExistingTextGenerationProtocol() {
        when(http.postJson(any(), anyMap(), anyString())).thenReturn(new AiProviderHttpResponse(200,
                mapper.writeValueAsString(Map.of("candidates", List.of(Map.of("content",
                        Map.of("parts", List.of(Map.of("text", "result")))))))));
        assertThat(new GoogleGenerateContentClient(properties(), mapper, http)
                .generate(new AiQuestionPrompt("system", "user"), Map.of())).isEqualTo("result");
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(eq(URI.create("https://example.com/v1beta/models/default-model:generateContent")),
                anyMap(), body.capture());
        var parts = mapper.readTree(body.getValue()).path("contents").get(0).path("parts");
        assertThat(parts.size()).isEqualTo(1);
        assertThat(parts.get(0).path("text").asString()).isEqualTo("user");
        assertThat(body.getValue()).doesNotContain("inlineData", "transcription_config");
    }

    private tools.jackson.databind.JsonNode captureRequest() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(http).postJson(eq(URI.create("https://example.com/v1beta/interactions")),
                argThat(headers -> "test-key".equals(headers.get("x-goog-api-key"))
                        && "application/json".equals(headers.get("Content-Type"))), body.capture());
        return mapper.readTree(body.getValue());
    }

    private GoogleAiSpeechTranscriptionClient client(AiProperties.Google properties) {
        return new GoogleAiSpeechTranscriptionClient(properties, mapper, http);
    }

    private AiProperties.Google properties() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("test-key");
        properties.setModel("default-model");
        properties.setBaseUrl("https://example.com/v1beta");
        return properties;
    }

    private AiProviderHttpResponse response(String text) {
        return new AiProviderHttpResponse(200, mapper.writeValueAsString(Map.of(
                "status", "completed", "steps", List.of(Map.of("type", "model_output",
                        "content", List.of(Map.of("type", "text", "text", text)))))));
    }
}
