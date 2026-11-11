package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleAiLiveSessionClientTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldRestrictTokenToOneShortLivedJapaneseVerbatimSession() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("private-key");
        var client = new GoogleAiLiveSessionClient(properties, mapper, (uri, headers, body) -> {
            assertThat(uri).isEqualTo(URI.create("https://generativelanguage.googleapis.com/v1alpha/auth_tokens"));
            assertThat(headers.get("x-goog-api-key")).isEqualTo("private-key");
            var request = mapper.readTree(body);
            assertThat(request.path("uses").asInt()).isEqualTo(1);
            assertThat(Instant.parse(request.path("expireTime").asString()))
                    .isBetween(Instant.now().plusSeconds(170), Instant.now().plusSeconds(181));
            assertThat(Instant.parse(request.path("newSessionExpireTime").asString()))
                    .isBetween(Instant.now().plusSeconds(50), Instant.now().plusSeconds(61));
            var setup = request.path("bidiGenerateContentSetup");
            assertThat(setup.path("model").asString()).isEqualTo("models/gemini-3.5-transcribe-live");
            assertThat(setup.path("generationConfig").path("responseModalities").get(0).asString()).isEqualTo("TEXT");
            assertThat(setup.path("inputAudioTranscription").path("languageCodes").get(0).asString()).isEqualTo("ja-JP");
            assertThat(setup.path("inputAudioTranscription").path("mode").asString()).isEqualTo("VERBATIM");
            assertThat(setup.path("realtimeInputConfig").path("automaticActivityDetection").path("disabled").asBoolean()).isTrue();
            assertThat(request.has("fieldMask")).isFalse();
            return new AiProviderHttpResponse(200, "{\"name\":\"auth_tokens/private-token\"}");
        });
        var result = client.createSession();
        assertThat(result.token()).isEqualTo("auth_tokens/private-token");
        assertThat(result.websocketUrl()).isEqualTo("wss://generativelanguage.googleapis.com/ws/"
                + "google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContentConstrained");
        assertThat(result.toString()).doesNotContain("private-token", "private-key");
    }

    @Test
    void shouldSanitizeErrorsAndRejectMissingTokens() {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("private-key");
        for (var response : new AiProviderHttpResponse[]{
                new AiProviderHttpResponse(503, "private-key"),
                new AiProviderHttpResponse(429, "private-token"),
                new AiProviderHttpResponse(200, "{\"name\":42}"),
                new AiProviderHttpResponse(200, "private-token")}) {
            var client = new GoogleAiLiveSessionClient(properties, mapper, (uri, headers, body) -> response);
            assertThatThrownBy(client::createSession).isInstanceOf(BusinessException.class)
                    .hasNoCause().hasMessageNotContaining("private-key").hasMessageNotContaining("private-token");
        }
    }
}
