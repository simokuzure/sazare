package com.sazare.service.ai.client;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.AiQuestionPrompt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GoogleAiChatImportClientTest {
    @Test
    void quotaAndUnavailableErrorsNeverRetryOrSwitchModels() {
        for (int status : new int[]{429, 503}) {
            AiProviderHttpClient http = mock(AiProviderHttpClient.class);
            when(http.postJson(any(), anyMap(), anyString())).thenReturn(new AiProviderHttpResponse(status, "{}"));
            assertThatThrownBy(() -> client(http).analyze(new AiQuestionPrompt("规则", "正文"))).hasMessageContaining("请求失败");
            verify(http, times(1)).postJson(any(), anyMap(), anyString());
        }
    }

    @Test
    void rejectsTruncatedOutputEvenIfJsonTextLooksValid() {
        AiProviderHttpClient http = mock(AiProviderHttpClient.class);
        when(http.postJson(any(), anyMap(), anyString())).thenReturn(new AiProviderHttpResponse(200,
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}"));
        assertThatThrownBy(() -> client(http).analyze(new AiQuestionPrompt("规则", "正文"))).hasMessageContaining("未完整完成");
        verify(http, times(1)).postJson(any(), anyMap(), anyString());
    }

    @Test
    void combinesNonThinkingPartsWithoutExtraRequests() {
        AiProviderHttpClient http = mock(AiProviderHttpClient.class);
        when(http.postJson(any(), anyMap(), anyString())).thenReturn(new AiProviderHttpResponse(200,
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"{\"},{\"text\":\"}\"}]}}]}"));
        assertThat(client(http).analyze(new AiQuestionPrompt("规则", "正文"))).isEqualTo("{}");
        verify(http, times(1)).postJson(any(), anyMap(), anyString());
    }

    private GoogleAiChatImportClient client(AiProviderHttpClient http) {
        AiProperties.Google properties = new AiProperties.Google();
        properties.setApiKey("test-key");
        properties.setModel("gemini-3.8-flash");
        properties.setBaseUrl("https://example.invalid");
        return new GoogleAiChatImportClient(properties, new ObjectMapper(), http);
    }
}
