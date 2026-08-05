package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewCycleQuestion {

    /** 周期题目主键ID。 */
    private Long id;

    /** 所属复习周期ID。 */
    private Long reviewCycleId;

    /** 实际题目ID，对应 questions.id。 */
    private Long questionId;

    /** 题目角色：ORIGINAL=原题，DERIVED=衍生题。 */
    private String questionRole;

    /** 当前状态：PENDING、RETRY或PASSED。 */
    private String reviewStatus;

    /** 该题在本周期的总作答次数。 */
    private Integer attemptCount;

    /** 最近一次SM-2质量分，尚未作答时为空。 */
    private Integer lastQuality;

    /** 最近作答时间。 */
    private LocalDateTime lastAttemptAt;

    /** 最近一次通过时间，未通过时为空。 */
    private LocalDateTime passedAt;

    /** 同优先级题目的稳定排序值。 */
    private Integer sortOrder;

    /** 加入周期的时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
