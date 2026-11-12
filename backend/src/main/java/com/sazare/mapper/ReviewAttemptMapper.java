package com.sazare.mapper;

import com.sazare.dto.ReviewAttemptHistoryRow;
import com.sazare.dto.ReviewSchedulingEvent;
import com.sazare.entity.ReviewAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewAttemptMapper {

    int insertAttempt(ReviewAttempt attempt);

    List<ReviewSchedulingEvent> selectSchedulingHistory(@Param("reviewCardId") Long reviewCardId);

    List<ReviewAttemptHistoryRow> selectReviewHistory(@Param("reviewCardId") Long reviewCardId,
                                                      @Param("userId") Long userId);

    boolean existsByCardIdAndUserAnswerId(@Param("reviewCardId") Long reviewCardId,
                                          @Param("userAnswerId") Long userAnswerId);
}
