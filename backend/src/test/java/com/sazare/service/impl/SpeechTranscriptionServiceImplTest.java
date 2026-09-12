package com.sazare.service.impl;

import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.AiSpeechTranscriptionClient;
import com.sazare.service.ai.client.AiProviderHttpException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpeechTranscriptionServiceImplTest {
    private final AiSpeechTranscriptionClient client = mock(AiSpeechTranscriptionClient.class);
    private final SpeechTranscriptionServiceImpl service = new SpeechTranscriptionServiceImpl(client, new ObjectMapper());

    @Test
    void shouldReturnTranscriptAndTreatBlankAsNoSpeech() {
        when(client.transcribe(any(), anyString())).thenReturn("{\"text\":\" 日本語 \"}");
        assertThat(service.transcribe(new byte[]{1}, "audio/webm").text()).isEqualTo("日本語");
        when(client.transcribe(any(), anyString())).thenReturn("{\"text\":\" \"}");
        assertThatThrownBy(() -> service.transcribe(new byte[]{1}, "audio/webm"))
                .hasMessage("未识别到语音，请重新录音");
    }

    @Test
    void shouldRejectMalformedResponsesWithoutLeakingContent() {
        for (String result : new String[]{null, "", "private-transcript", "{}", "null", "[]",
                "{\"text\":42}", "{\"text\":null}", "{\"text\":\"私的内容\",\"extra\":1}"}) {
            when(client.transcribe(any(), anyString())).thenReturn(result);
            assertThatThrownBy(() -> service.transcribe(new byte[]{1}, "audio/ogg"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("语音转写结果格式错误，请重新录音").hasNoCause();
        }
    }

    @Test
    void shouldSanitizeRateLimitsServerErrorsAndTimeoutsWithoutRetrying() {
        for (RuntimeException exception : new RuntimeException[]{
                new AiProviderHttpException(429, "private-transcript"),
                new AiProviderHttpException(500, "private-transcript"),
                new BusinessException(ErrorCode.BUSINESS_ERROR, "timeout", new java.net.http.HttpTimeoutException("secret"))}) {
            reset(client);
            when(client.transcribe(any(), anyString())).thenThrow(exception);
            assertThatThrownBy(() -> service.transcribe(new byte[]{1}, "audio/webm"))
                    .isInstanceOf(BusinessException.class).hasNoCause()
                    .hasMessageContaining(exception instanceof AiProviderHttpException e && e.getStatusCode() == 429
                            ? "过于频繁" : "语音转写");
            verify(client, times(1)).transcribe(any(), anyString());
        }
    }
}
