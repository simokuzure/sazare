package com.sazare.vo;

import java.util.List;

public record ChatImportAnalysisVO(
        List<Integer> checkedUserMessageIds,
        List<Candidate> candidates
) {
    public record Evidence(int messageId, String originalText) { }

    public record Candidate(
            Long errorTypeId,
            String name,
            String description,
            String severity,
            List<Evidence> evidence,
            String correctedExpression,
            String reviewSourceText,
            String referenceAnswer
    ) { }
}
