package com.jt.learning.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ReviewCard {

    /** 复习卡片主键ID。 */
    private Long id;

    /** 卡片所属用户ID，对应 users.id。 */
    private Long userId;

    /** 用户错误类型ID，对应 user_error_types.id。 */
    private Long userErrorTypeId;

    /** 卡片状态：ACTIVE=复习中或等待到期，MASTERED=已掌握。 */
    private String status;

    /** SM-2难度因子，最低为1.3000。 */
    private BigDecimal easeFactor;

    /** 当前连续成功次数，失败时归零。 */
    private Integer repetitionCount;

    /** 当前SM-2复习间隔天数。 */
    private Integer intervalDays;

    /** 累计失败次数。 */
    private Integer lapseCount;

    /** 下次应复习时间，已掌握时为空。 */
    private LocalDateTime dueAt;

    /** 最近一次影响SM-2状态的时间。 */
    private LocalDateTime lastReviewedAt;

    /** 最近一次完成周期的时间，激活时为空。 */
    private LocalDateTime masteredAt;

    /** 逻辑删除标记。 */
    private Boolean deleted;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
