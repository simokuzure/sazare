package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserAnswerError {

    private Long id;
    private Long userAnswerId;
    private Long userId;
    private Long questionId;
    private Long errorTypeId;
    private Long userErrorTypeId;
    private String originalText;
    private String issue;
    private String suggestion;
    private String severity;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
