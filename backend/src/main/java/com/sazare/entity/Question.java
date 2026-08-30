package com.sazare.entity;

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
     * 题目类型：TRANSLATION_ZH_TO_JA=中译日短句，TRANSLATION_ZH_TO_JA_ARTICLE=中译日文章
     */
    private String questionType;

    /**
     * 中文题目原文，文章题按句使用双换行分隔
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
     * 短句语法点或文章生词提示
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
     * 题目来源：AI=AI生成，MANUAL=人工录入，REVIEW_DERIVED=复习衍生题
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
