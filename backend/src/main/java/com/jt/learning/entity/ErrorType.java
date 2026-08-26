package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ErrorType {

    private Long id;
    private Long parentId;
    private Integer typeLevel;
    private String code;
    private String name;
    private String description;
    private String nameEn;
    private String descriptionEn;
    private Integer sortOrder;
    private Boolean enabled;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
