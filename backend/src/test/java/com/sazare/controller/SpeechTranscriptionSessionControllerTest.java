package com.sazare.controller;

import com.sazare.config.AiProperties;
import com.sazare.exception.GlobalExceptionHandler;
import com.sazare.service.impl.SpeechTranscriptionSessionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpeechTranscriptionSessionControllerTest {
    @Test
    void shouldExposeCurrentModeAndModelWithoutCachingOrCreatingTokens() throws Exception {
        var properties = new AiProperties();
        var service = new SpeechTranscriptionSessionServiceImpl(properties, null);
        var mvc = MockMvcBuilders.standaloneSetup(new SpeechTranscriptionSessionController(service)).build();
        mvc.perform(get("/speech-transcription-config"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.mode").value("live"))
                .andExpect(jsonPath("$.data.model").value("gemini-3.5-transcribe-live"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        properties.setSpeechTranscriptionMode(AiProperties.SpeechTranscriptionMode.FILE);
        mvc.perform(get("/speech-transcription-config"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.mode").value("file"))
                .andExpect(jsonPath("$.data.model").value("gemini-3.5-transcribe"));
    }

    @Test
    void shouldRejectStaleLiveSessionRequestInFileMode() throws Exception {
        var properties = new AiProperties();
        properties.setSpeechTranscriptionMode(AiProperties.SpeechTranscriptionMode.FILE);
        var service = new SpeechTranscriptionSessionServiceImpl(properties, null);
        var mvc = MockMvcBuilders.standaloneSetup(new SpeechTranscriptionSessionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/speech-transcription-sessions"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("当前配置为文件转写，请重新开始录音"));
    }
}
