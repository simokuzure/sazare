package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.service.SpeechTranscriptionSessionService;
import com.sazare.vo.SpeechTranscriptionSessionVO;
import com.sazare.vo.SpeechTranscriptionConfigVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpeechTranscriptionSessionController {
    private final SpeechTranscriptionSessionService service;

    public SpeechTranscriptionSessionController(SpeechTranscriptionSessionService service) {
        this.service = service;
    }

    @GetMapping("/speech-transcription-config")
    public ApiResponse<SpeechTranscriptionConfigVO> configuration(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(service.getConfig());
    }

    @PostMapping("/speech-transcription-sessions")
    public ApiResponse<SpeechTranscriptionSessionVO> create(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ApiResponse.success(service.createSession());
    }
}
