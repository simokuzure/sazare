package com.jt.learning.mapper;

import com.jt.learning.dto.ReviewCycleProgressRow;
import com.jt.learning.entity.ReviewCycleQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReviewCycleQuestionMapper {

    int insertQuestion(ReviewCycleQuestion question);

    int insertQuestionIfAbsent(ReviewCycleQuestion question);

    ReviewCycleQuestion selectByIdAndCycleId(@Param("id") Long id, @Param("reviewCycleId") Long reviewCycleId);

    ReviewCycleQuestion selectByCycleIdAndQuestionId(@Param("reviewCycleId") Long reviewCycleId,
                                                     @Param("questionId") Long questionId);

    ReviewCycleQuestion selectLatestRetry(@Param("reviewCycleId") Long reviewCycleId);

    ReviewCycleQuestion selectRandomPendingOriginal(@Param("reviewCycleId") Long reviewCycleId);

    ReviewCycleQuestion selectActiveDerived(@Param("reviewCycleId") Long reviewCycleId);

    ReviewCycleQuestion selectLatestFailedOriginal(@Param("reviewCycleId") Long reviewCycleId);

    List<ReviewCycleQuestion> selectAllByCycleId(@Param("reviewCycleId") Long reviewCycleId);

    ReviewCycleProgressRow selectProgress(@Param("reviewCycleId") Long reviewCycleId,
                                          @Param("verificationRequiredAfter") LocalDateTime verificationRequiredAfter);

    int markAttempt(@Param("id") Long id, @Param("reviewStatus") String reviewStatus,
                    @Param("attemptCount") int attemptCount, @Param("lastQuality") int lastQuality,
                    @Param("lastAttemptAt") LocalDateTime lastAttemptAt,
                    @Param("passedAt") LocalDateTime passedAt,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int markPracticeFailure(@Param("id") Long id, @Param("attemptCount") int attemptCount,
                            @Param("attemptedAt") LocalDateTime attemptedAt);
}
