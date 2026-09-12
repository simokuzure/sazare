package com.sazare.controller;

import com.sazare.exception.BusinessException;
import com.sazare.exception.GlobalExceptionHandler;
import com.sazare.service.SpeechTranscriptionService;
import com.sazare.vo.SpeechTranscriptionVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SpeechTranscriptionControllerTest {
    private final SpeechTranscriptionService service = mock(SpeechTranscriptionService.class);
    private final SpeechTranscriptionController controller = new SpeechTranscriptionController(service);

    @Test
    void shouldNormalizeMimeTypeReturnTextAndClearAudio() throws Exception {
        byte[][] received = new byte[1][];
        when(service.transcribe(any(), eq("audio/webm"))).thenAnswer(invocation -> {
            received[0] = invocation.getArgument(0);
            assertThat(received[0]).containsExactly(1, 2, 3);
            return new SpeechTranscriptionVO("日本語");
        });
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(post("/speech-transcriptions").contentType("audio/webm;codecs=opus")
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.text").value("日本語"));
        assertThat(received[0]).containsExactly(0, 0, 0);
    }

    @Test
    void shouldRejectEmptyAndUnsupportedAudioWithoutCallingAi() {
        for (String type : Arrays.asList(null, "application/json", "audio/wav", "invalid")) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType(type);
            request.setContent(new byte[]{1});
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThatThrownBy(() -> controller.transcribe(request, response))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("音频格式");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }
        MockHttpServletRequest request = request(new byte[0]);
        assertThatThrownBy(() -> controller.transcribe(request, new MockHttpServletResponse()))
                .hasMessage("音频不能为空");
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectOversizedBodyEvenWithoutLengthHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContentType("audio/ogg");
        request.setContent(new byte[SpeechTranscriptionController.MAX_AUDIO_BYTES + 1]);
        assertThatThrownBy(() -> controller.transcribe(request, new MockHttpServletResponse()))
                .hasMessage("音频大小不能超过 8 MiB");
        verifyNoInteractions(service);
    }

    @Test
    void shouldAcceptExactLimitAndClearAudioWhenAiFails() {
        byte[][] received = new byte[1][];
        when(service.transcribe(any(), eq("audio/ogg"))).thenAnswer(invocation -> {
            received[0] = invocation.getArgument(0);
            assertThat(received[0]).hasSize(SpeechTranscriptionController.MAX_AUDIO_BYTES);
            throw new BusinessException(com.sazare.exception.ErrorCode.BUSINESS_ERROR, "转写失败");
        });
        byte[] audio = new byte[SpeechTranscriptionController.MAX_AUDIO_BYTES];
        Arrays.fill(audio, (byte) 7);
        assertThatThrownBy(() -> controller.transcribe(request(audio), new MockHttpServletResponse()))
                .hasMessage("转写失败");
        assertThat(received[0]).containsOnly((byte) 0);
    }

    private MockHttpServletRequest request(byte[] audio) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("audio/ogg");
        request.setContent(audio);
        return request;
    }
}
