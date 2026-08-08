package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionEmbeddingCandidate {

    private Long questionId;
    private String sourceText;
    private String contextText;
    private String contentHash;
    private String modelName;
}
