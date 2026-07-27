package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Question {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 题目类型：TRANSLATION_ZH_TO_JA=中译日
     */
    private String questionType;

    /**
     * 题目原文，当前为中文句子
     */
    private String sourceText;

    /**
     * 题目语境说明
     */
    private String contextText;

    /**
     * JLPT等级：N5、N4、N3、N2、N1
     */
    private String level;

    /**
     * 难度等级，范围1到5
     */
    private Integer difficulty;

    /**
     * 语法点说明
     */
    private String grammarPoint;

    /**
     * 是否口语表达
     */
    private Boolean spoken;

    /**
     * 是否商务表达
     */
    private Boolean business;

    /**
     * 是否考试相关
     */
    private Boolean exam;

    /**
     * 题目来源：AI=AI生成，MANUAL=人工录入
     */
    private String sourceType;

    /**
     * 是否启用
     */
    private Boolean enabled;

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
