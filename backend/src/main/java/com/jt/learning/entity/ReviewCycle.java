package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewCycle {

    /** 复习周期主键ID。 */
    private Long id;

    /** 所属复习卡片ID。 */
    private Long reviewCardId;

    /** 卡片周期序号，从1开始递增。 */
    private Integer cycleNo;

    /** 周期状态：IN_PROGRESS=进行中，COMPLETED=已完成。 */
    private String status;

    /** 本周期最低成功次数。 */
    private Integer targetSuccessCount;

    /** 本周期累计成功作答次数。 */
    private Integer successfulReviewCount;

    /** 最终衍生题通过时间必须晚于该时间。 */
    private LocalDateTime verificationRequiredAfter;

    /** 周期开始时间。 */
    private LocalDateTime startedAt;

    /** 周期完成时间，进行中时为空。 */
    private LocalDateTime completedAt;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
