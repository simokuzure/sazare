package com.sazare.dto;

import com.sazare.vo.ChatImportAnalysisVO;
import java.util.List;

public record AiChatImportAnalysisDTO(
        List<MessageCheck> messageChecks,
        List<ChatImportAnalysisVO.Candidate> candidates
) {
    public record MessageCheck(Integer messageId, List<Finding> findings) { }

    public record Finding(String originalText, Integer candidateIndex) { }
}
