package com.sazare.service;

import com.sazare.dto.ChatImportConfirmationRequest;
import com.sazare.vo.ChatImportAnalysisVO;
import com.sazare.vo.ChatImportConfirmationVO;

public interface ChatImportService {
    ChatImportAnalysisVO analyze(byte[] markdown, String learningMode);
    ChatImportConfirmationVO confirm(ChatImportConfirmationRequest request);
}
