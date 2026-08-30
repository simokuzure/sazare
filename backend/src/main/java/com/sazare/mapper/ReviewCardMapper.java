package com.sazare.mapper;

import com.sazare.dto.ReviewCardListRow;
import com.sazare.entity.ReviewCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReviewCardMapper {

    long countCards(@Param("userId") Long userId, @Param("status") String status,
                    @Param("learningMode") String learningMode,
                    @Param("dueOnly") boolean dueOnly, @Param("now") LocalDateTime now);

    List<ReviewCardListRow> selectCardList(@Param("userId") Long userId, @Param("status") String status,
                                           @Param("learningMode") String learningMode,
                                           @Param("dueOnly") boolean dueOnly, @Param("now") LocalDateTime now,
                                           @Param("limit") int limit, @Param("offset") long offset);

    ReviewCard selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    ReviewCard selectForUpdateByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    ReviewCard selectForUpdateByUserErrorTypeId(@Param("userErrorTypeId") Long userErrorTypeId);

    int insertCardIfAbsent(ReviewCard card);

    int logicalDelete(@Param("id") Long id, @Param("userId") Long userId,
                      @Param("updatedAt") LocalDateTime updatedAt);

    int updateSchedule(@Param("id") Long id, @Param("status") String status,
                       @Param("easeFactor") BigDecimal easeFactor,
                       @Param("repetitionCount") int repetitionCount,
                       @Param("intervalDays") int intervalDays,
                       @Param("lapseCount") int lapseCount,
                       @Param("dueAt") LocalDateTime dueAt,
                       @Param("lastReviewedAt") LocalDateTime lastReviewedAt,
                       @Param("masteredAt") LocalDateTime masteredAt,
                       @Param("updatedAt") LocalDateTime updatedAt);
}
