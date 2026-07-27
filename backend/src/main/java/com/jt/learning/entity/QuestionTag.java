package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class QuestionTag {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 题目ID，对应 questions.id，由代码维护有效性
     */
    private Long questionId;

    /**
     * 标签ID，对应 tags.id，由代码维护有效性
     */
    private Long tagId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
