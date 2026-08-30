package com.sazare.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class QuestionAnswer {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 题目ID，对应 questions.id，由代码维护有效性
     */
    private Long questionId;

    /**
     * 答案文本，当前为日语表达
     */
    private String answerText;

    /**
     * 答案类型：STANDARD=标准答案，REFERENCE=参考答案
     */
    private String answerType;

    /**
     * 是否主答案
     */
    private Boolean primaryAnswer;

    /**
     * 排序值，数值越小越靠前
     */
    private Integer sortOrder;

    /**
     * 是否删除
     */
    private Boolean deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
