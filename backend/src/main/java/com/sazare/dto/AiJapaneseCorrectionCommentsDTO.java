package com.sazare.dto;

public record AiJapaneseCorrectionCommentsDTO(
        String grammarVocabularyComment,
        String naturalFluencyComment,
        String styleConsistencyComment,
        String writingCompletenessComment
) {
}
