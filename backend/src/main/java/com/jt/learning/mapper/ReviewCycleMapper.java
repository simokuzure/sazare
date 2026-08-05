package com.jt.learning.mapper;

import com.jt.learning.entity.ReviewCycle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReviewCycleMapper {

    int insertCycle(ReviewCycle cycle);

    ReviewCycle selectCurrentByCardId(@Param("reviewCardId") Long reviewCardId);

    ReviewCycle selectLatestByCardId(@Param("reviewCardId") Long reviewCardId);

    ReviewCycle selectCurrentForUpdateByCardId(@Param("reviewCardId") Long reviewCardId);

    int selectMaxCycleNo(@Param("reviewCardId") Long reviewCardId);

    int updateProgress(@Param("id") Long id,
                       @Param("targetSuccessCount") int targetSuccessCount,
                       @Param("successfulReviewCount") int successfulReviewCount,
                       @Param("verificationRequiredAfter") LocalDateTime verificationRequiredAfter,
                       @Param("updatedAt") LocalDateTime updatedAt);

    int completeCycle(@Param("id") Long id, @Param("completedAt") LocalDateTime completedAt);
}
