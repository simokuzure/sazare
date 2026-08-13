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

    /** 本周期目标净成功次数，固定为4。 */
    private Integer targetSuccessCount;

    /** 本周期累计成功作答次数。 */
    private Integer successfulReviewCount;

    /** 本周期累计失败次数，不包含创建新卡片的首次错误。 */
    private Integer failedReviewCount;

    /** 兼容字段，记录最近新增或再次答错原题的时间。 */
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
