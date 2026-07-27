package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class UserAnswer {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID，对应 users.id，由代码维护有效性
     */
    private Long userId;

    /**
     * 题目ID，对应 questions.id，由代码维护有效性
     */
    private Long questionId;

    /**
     * 用户提交的日语答案
     */
    private String answerText;

    /**
     * 回答状态：SUBMITTED=已提交，REVIEWED=已评测，FAILED=评测失败
     */
    private String answerStatus;

    /**
     * 语法与词汇正确性评分，满分100分
     */
    private Integer grammarVocabularyScore;

    /**
     * 自然度与流畅度评分，满分100分
     */
    private Integer naturalFluencyScore;

    /**
     * 场景适配度评分，满分100分
     */
    private Integer scenarioAdaptationScore;

    /**
     * 表达信息完整性评分，满分100分
     */
    private Integer informationCompletenessScore;

    /**
     * 总分，四项评分均值，由代码计算
     */
    private BigDecimal totalScore;

    /**
     * AI总体评价
     */
    private String aiOverallComment;

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
