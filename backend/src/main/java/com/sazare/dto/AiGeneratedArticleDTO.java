package com.sazare.dto;

import java.util.List;

public record AiGeneratedArticleDTO(
        String questionType,
        String contextText,
        String level,
        Integer difficulty,
        String grammarPoint,
        Boolean spoken,
        Boolean business,
        Boolean exam,
        List<AiArticleSentenceDTO> sentences
) {
}
