package com.sazare.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UserErrorTypeListItemRow {

    private Long id;

    private String learningMode;
    private Long errorTypeId;
    private String errorTypeCode;
    private String errorTypeName;
    private String name;
    private String description;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
