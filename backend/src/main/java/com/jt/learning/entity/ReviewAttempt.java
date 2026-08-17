package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewAttempt {

    /** 复习事件主键ID。 */
    private Long id;

    /** 作答用户ID。 */
    private Long userId;

    /** 本次事件影响的复习卡片ID。 */
    private Long reviewCardId;

    /** 本次事件所属复习周期ID。 */
    private Long reviewCycleId;

    /** 本次事件对应周期题目ID。 */
    private Long reviewCycleQuestionId;

    /** 实际用户答案ID。 */
    private Long userAnswerId;

    /** 事件来源：REVIEW或PRACTICE_ERROR。 */
    private String attemptSource;

    /** 本次结果：PASS或FAIL。 */
    private String result;

    /** SM-2质量分，范围0到5。 */
    private Integer sm2Quality;

    /** 本次回答是否掌握复习重点。 */
    private Boolean targetErrorResolved;

    /** 复习专用AI反馈。 */
    private String aiFeedback;

    /** 处理本次事件后的SM-2难度因子。 */
    private BigDecimal easeFactorAfter;

    /** 处理本次事件后的连续成功次数。 */
    private Integer repetitionCountAfter;

    /** 处理本次事件后的复习间隔天数。 */
    private Integer intervalDaysAfter;

    /** 处理本次事件后的周期累计成功次数。 */
    private Integer cycleSuccessCountAfter;

    /** 本次事件计算出的下次复习时间，周期完成时为空。 */
    private LocalDateTime nextDueAt;

    /** 事件发生时间。 */
    private LocalDateTime createdAt;
}
