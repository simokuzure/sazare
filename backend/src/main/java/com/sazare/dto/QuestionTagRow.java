package com.sazare.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionTagRow {

    private Long questionId;

    private Long id;

    private String tagType;

    private Long parentId;

    private String code;

    private String name;

    private String description;

    private String nameEn;

    private String descriptionEn;

    private Integer sortOrder;
}
