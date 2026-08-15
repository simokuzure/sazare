package com.jt.learning.dto;

import java.util.List;

public record AiArticleRetryContext(
        String rejectionReason,
        String rejectedArticle,
        List<QuestionEmbeddingMatch> matchedHistoricalArticles
) {
}
