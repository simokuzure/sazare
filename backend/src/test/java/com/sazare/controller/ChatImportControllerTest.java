package com.sazare.controller;

import com.sazare.exception.GlobalExceptionHandler;
import com.sazare.service.ChatImportService;
import com.sazare.vo.ChatImportAnalysisVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatImportControllerTest {
    private final ChatImportService service = mock(ChatImportService.class);
    private final ChatImportController controller = new ChatImportController(service);

    @Test
    void acceptsRawMarkdownAndValidatesMode() throws Exception {
        when(service.analyze(any(), eq("EN_TO_JA"))).thenReturn(new ChatImportAnalysisVO(List.of(1), List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/chat-imports/analyses?learningMode=EN_TO_JA").contentType("text/markdown")
                        .content("## User\n日本語"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/chat-imports/analyses?learningMode=INVALID").contentType("text/markdown").content("bad"))
                .andExpect(jsonPath("$.message").value("学习模式只能是 ZH_TO_JA 或 EN_TO_JA"));
        verify(service, times(1)).analyze(any(), anyString());
    }

    @Test
    void rejectsBodyOverLimitWithoutTrustingContentLength() {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[131_073]);
        assertThatThrownBy(() -> controller.analyze(request, new MockHttpServletResponse(), "ZH_TO_JA"))
                .hasMessageContaining("128 KiB");
        verifyNoInteractions(service);
    }

    @Test
    void confirmationRequiresNonEmptyValidItems() throws Exception {
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(post("/chat-imports/confirmations").contentType("application/json")
                        .content("{\"learningMode\":\"ZH_TO_JA\",\"items\":[]}"))
                .andExpect(jsonPath("$.message").value("items: 请选择需要确认的候选"));
        verifyNoInteractions(service);
    }
}
