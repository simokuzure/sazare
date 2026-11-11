package com.sazare.service.impl;

import com.sazare.config.AiProperties;
import com.sazare.service.ai.client.GoogleAiLiveSessionClient;
import com.sazare.vo.SpeechTranscriptionSessionVO;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SpeechTranscriptionSessionServiceImplTest {
    private final AiProperties properties = new AiProperties();
    private final GoogleAiLiveSessionClient client = mock(GoogleAiLiveSessionClient.class);
    private final SpeechTranscriptionSessionServiceImpl service = new SpeechTranscriptionSessionServiceImpl(properties, client);

    @Test
    void shouldReportIndependentModelsAndCreateLiveSessionEvenWithOpenAiTextProvider() {
        properties.setProvider("openai");
        properties.getProviders().getGoogle().setLiveTranscriptionModel("live-model");
        var session = new SpeechTranscriptionSessionVO("token", "wss://example.test", Map.of(), false);
        when(client.createSession()).thenReturn(session);
        assertThat(service.getConfig().mode()).isEqualTo("live");
        assertThat(service.getConfig().model()).isEqualTo("live-model");
        assertThat(service.createSession()).isSameAs(session);
    }

    @Test
    void shouldReportFileModelAndNeverIssueLiveTokenInFileMode() {
        properties.setSpeechTranscriptionMode(AiProperties.SpeechTranscriptionMode.FILE);
        properties.getProviders().getGoogle().setTranscriptionModel("file-model");
        assertThat(service.getConfig().mode()).isEqualTo("file");
        assertThat(service.getConfig().model()).isEqualTo("file-model");
        assertThatThrownBy(service::createSession).hasMessage("当前配置为文件转写，请重新开始录音");
        verifyNoInteractions(client);
    }

    @Test
    void shouldPreserveMockProviderWithoutExternalCalls() {
        properties.setProvider("mock");
        assertThat(service.createSession().mock()).isTrue();
        verifyNoInteractions(client);
    }
}
