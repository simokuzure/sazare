package com.jt.learning.vo;

public record JapaneseCorrectionCommentsVO(
        String grammarVocabularyComment,
        String naturalFluencyComment,
        String styleConsistencyComment,
        String writingCompletenessComment
) {
}
