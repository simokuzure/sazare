package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.SpeechTranscriptionService;
import com.sazare.vo.SpeechTranscriptionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@RestController
public class SpeechTranscriptionController {

    static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    private static final Set<String> SUPPORTED_TYPES = Set.of("audio/webm", "audio/ogg");
    private final SpeechTranscriptionService service;

    public SpeechTranscriptionController(SpeechTranscriptionService service) {
        this.service = service;
    }

    @PostMapping("/speech-transcriptions")
    public ApiResponse<SpeechTranscriptionVO> transcribe(
            HttpServletRequest request, HttpServletResponse response
    ) {
        response.setHeader("Cache-Control", "no-store");
        String mimeType = normalizeMimeType(request.getContentType());
        if (request.getContentLengthLong() > MAX_AUDIO_BYTES) {
            throw invalid("音频大小不能超过 8 MiB");
        }
        byte[] audio;
        try {
            // 限制实际读取量，分块传输或不准确的长度头也不能导致无界分配。
            audio = request.getInputStream().readNBytes(MAX_AUDIO_BYTES + 1);
        } catch (IOException exception) {
            throw invalid("音频读取失败，请重新录音");
        }
        try {
            if (audio.length == 0) throw invalid("音频不能为空");
            if (audio.length > MAX_AUDIO_BYTES) throw invalid("音频大小不能超过 8 MiB");
            return ApiResponse.success(service.transcribe(audio, mimeType));
        } finally {
            Arrays.fill(audio, (byte) 0);
        }
    }

    private String normalizeMimeType(String contentType) {
        try {
            MediaType type = MediaType.parseMediaType(contentType == null ? "" : contentType);
            String normalized = (type.getType() + "/" + type.getSubtype()).toLowerCase(Locale.ROOT);
            if (SUPPORTED_TYPES.contains(normalized)) return normalized;
        } catch (IllegalArgumentException ignored) {
            // 不将用户提交的媒体类型写入异常或日志。
        }
        throw invalid("音频格式只支持 audio/webm 或 audio/ogg");
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }
}
