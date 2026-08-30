package com.sazare.mapper;

import com.sazare.dto.LearningStatisticsCurrentReviewRow;
import com.sazare.dto.LearningStatisticsDailyTrendRow;
import com.sazare.dto.LearningStatisticsOverviewRow;
import com.sazare.dto.LearningStatisticsPeriodReviewRow;
import com.sazare.dto.LearningStatisticsScoreDimensionsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

    List<LocalDate> selectLearningActivityDates(@Param("userId") Long userId);

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
}
