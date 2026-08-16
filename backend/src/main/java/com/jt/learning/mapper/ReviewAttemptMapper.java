package com.jt.learning.mapper;

import com.jt.learning.dto.ReviewAttemptHistoryRow;
import com.jt.learning.entity.ReviewAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewAttemptMapper {

    int insertAttempt(ReviewAttempt attempt);

    List<ReviewAttemptHistoryRow> selectReviewHistory(@Param("reviewCardId") Long reviewCardId,
                                                      @Param("userId") Long userId);

    boolean existsByCardIdAndUserAnswerId(@Param("reviewCardId") Long reviewCardId,
                                          @Param("userAnswerId") Long userAnswerId);
}
