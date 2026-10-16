package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.dto.ChatImportConfirmationRequest;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ChatImportService;
import com.sazare.util.ChatMarkdownParser;
import com.sazare.vo.ChatImportAnalysisVO;
import com.sazare.vo.ChatImportConfirmationVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/chat-imports")
public class ChatImportController {
    private final ChatImportService service;

    public ChatImportController(ChatImportService service) { this.service = service; }

    @PostMapping(value = "/analyses", consumes = {"text/markdown", "application/octet-stream"})
    public ApiResponse<ChatImportAnalysisVO> analyze(
            HttpServletRequest request, HttpServletResponse response,
            @RequestParam(defaultValue = "ZH_TO_JA")
            @Pattern(regexp = "ZH_TO_JA|EN_TO_JA", message = "学习模式只能是 ZH_TO_JA 或 EN_TO_JA") String learningMode) {
        response.setHeader("Cache-Control", "no-store");
        if (request.getContentLengthLong() > ChatMarkdownParser.MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "聊天文件不能超过 128 KiB");
        }
        byte[] bytes;
        try {
            bytes = request.getInputStream().readNBytes(ChatMarkdownParser.MAX_FILE_BYTES + 1);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "聊天文件读取失败");
        }
        try {
            if (bytes.length > ChatMarkdownParser.MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "聊天文件不能超过 128 KiB");
            }
            return ApiResponse.success(service.analyze(bytes, learningMode));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @PostMapping("/confirmations")
    public ApiResponse<ChatImportConfirmationVO> confirm(@Valid @RequestBody ChatImportConfirmationRequest request) {
        return ApiResponse.success(service.confirm(request));
    }
}
