package com.jt.learning.mapper;

import com.jt.learning.entity.ReviewAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReviewAttemptMapper {

    int insertAttempt(ReviewAttempt attempt);

    boolean existsByCardIdAndUserAnswerId(@Param("reviewCardId") Long reviewCardId,
                                          @Param("userAnswerId") Long userAnswerId);
}
