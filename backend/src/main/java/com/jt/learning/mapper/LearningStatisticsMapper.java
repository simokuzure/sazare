package com.jt.learning.mapper;

import com.jt.learning.dto.LearningStatisticsCurrentReviewRow;
import com.jt.learning.dto.LearningStatisticsDailyTrendRow;
import com.jt.learning.dto.LearningStatisticsOverviewRow;
import com.jt.learning.dto.LearningStatisticsPeriodReviewRow;
import com.jt.learning.dto.LearningStatisticsScoreDimensionsRow;
import com.jt.learning.dto.LearningStatisticsWeaknessRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LearningStatisticsMapper {

    LearningStatisticsOverviewRow selectOverview(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    LearningStatisticsOverviewRow selectCorrectionOverview(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    long countConfirmedErrors(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<LearningStatisticsDailyTrendRow> selectDailyTrends(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<LearningStatisticsDailyTrendRow> selectCorrectionDailyTrends(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    LearningStatisticsScoreDimensionsRow selectScoreDimensions(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    LearningStatisticsScoreDimensionsRow selectCorrectionScoreDimensions(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    List<LearningStatisticsWeaknessRow> selectTopWeaknesses(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    LearningStatisticsCurrentReviewRow selectCurrentReviewSummary(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("now") LocalDateTime now
    );

    LearningStatisticsPeriodReviewRow selectPeriodReviewSummary(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );

    long countCompletedReviewCycles(
            @Param("userId") Long userId,
            @Param("learningMode") String learningMode,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
