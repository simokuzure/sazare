package com.jt.learning.service.impl;

import com.jt.learning.dto.LearningStatisticsCurrentReviewRow;
import com.jt.learning.dto.LearningStatisticsDailyTrendRow;
import com.jt.learning.dto.LearningStatisticsOverviewRow;
import com.jt.learning.dto.LearningStatisticsPeriodReviewRow;
import com.jt.learning.dto.LearningStatisticsQueryRequest;
import com.jt.learning.dto.LearningStatisticsScoreDimensionsRow;
import com.jt.learning.dto.LearningStatisticsWeaknessRow;
import com.jt.learning.entity.User;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.LearningStatisticsMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.LearningStatisticsService;
import com.jt.learning.vo.LearningStatisticsDailyTrendVO;
import com.jt.learning.vo.LearningStatisticsOverviewVO;
import com.jt.learning.vo.LearningStatisticsPeriodVO;
import com.jt.learning.vo.LearningStatisticsReviewOverviewVO;
import com.jt.learning.vo.LearningStatisticsScoreDimensionsVO;
import com.jt.learning.vo.LearningStatisticsVO;
import com.jt.learning.vo.LearningStatisticsWeaknessVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LearningStatisticsServiceImpl implements LearningStatisticsService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String RANGE_LAST_7_DAYS = "LAST_7_DAYS";
    private static final String RANGE_LAST_30_DAYS = "LAST_30_DAYS";
    private static final String RANGE_LAST_90_DAYS = "LAST_90_DAYS";
    private static final String RANGE_CUSTOM = "CUSTOM";
    private static final String CARD_ACTIVE = "ACTIVE";
    private static final String CARD_MASTERED = "MASTERED";

    private final UserMapper userMapper;
    private final LearningStatisticsMapper learningStatisticsMapper;
    private final Clock learningStatisticsClock;

    public LearningStatisticsServiceImpl(
            UserMapper userMapper,
            LearningStatisticsMapper learningStatisticsMapper,
            Clock learningStatisticsClock
    ) {
        this.userMapper = userMapper;
        this.learningStatisticsMapper = learningStatisticsMapper;
        this.learningStatisticsClock = learningStatisticsClock;
    }

    @Override
    public LearningStatisticsVO getLearningStatistics(LearningStatisticsQueryRequest request) {
        StatisticsPeriod period = resolvePeriod(request);
        User user = requireLocalUser();

        LearningStatisticsOverviewRow overview = learningStatisticsMapper.selectOverview(
                user.getId(), period.startAt(), period.endAt());
        long confirmedErrorCount = learningStatisticsMapper.countConfirmedErrors(
                user.getId(), period.startAt(), period.endAt());
        LearningStatisticsScoreDimensionsRow scoreDimensions = learningStatisticsMapper.selectScoreDimensions(
                user.getId(), period.startAt(), period.endAt());
        LearningStatisticsCurrentReviewRow currentReview = learningStatisticsMapper.selectCurrentReviewSummary(
                user.getId(), LocalDateTime.now(learningStatisticsClock));
        LearningStatisticsPeriodReviewRow periodReview = learningStatisticsMapper.selectPeriodReviewSummary(
                user.getId(), period.startAt(), period.endAt());
        long completedCycleCount = learningStatisticsMapper.countCompletedReviewCycles(
                user.getId(), period.startAt(), period.endAt());

        return new LearningStatisticsVO(
                new LearningStatisticsPeriodVO(period.range(), period.startDate(), period.endDate()),
                toOverview(overview, confirmedErrorCount),
                fillDailyTrends(period, learningStatisticsMapper.selectDailyTrends(user.getId(), period.startAt(), period.endAt())),
                toScoreDimensions(scoreDimensions),
                learningStatisticsMapper.selectTopWeaknesses(user.getId(), period.startAt(), period.endAt())
                        .stream()
                        .map(row -> toWeakness(row, LocalDateTime.now(learningStatisticsClock)))
                        .toList(),
                toReviewOverview(currentReview, periodReview, completedCycleCount)
        );
    }

    private StatisticsPeriod resolvePeriod(LearningStatisticsQueryRequest request) {
        String range = request.range();
        LocalDate today = LocalDate.now(learningStatisticsClock);
        if (RANGE_CUSTOM.equals(range)) {
            if (request.startDate() == null || request.endDate() == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "CUSTOM 范围必须同时提供 startDate 和 endDate");
            }
            if (request.startDate().isAfter(request.endDate())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "startDate 不能晚于 endDate");
            }
            return createPeriod(range, request.startDate(), request.endDate());
        }
        if (request.startDate() != null || request.endDate() != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "预设范围不能提供 startDate 或 endDate");
        }
        return switch (range) {
            case RANGE_LAST_7_DAYS -> createPeriod(range, today.minusDays(6), today);
            case RANGE_LAST_30_DAYS -> createPeriod(range, today.minusDays(29), today);
            case RANGE_LAST_90_DAYS -> createPeriod(range, today.minusDays(89), today);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的统计范围");
        };
    }

    private StatisticsPeriod createPeriod(String range, LocalDate startDate, LocalDate endDate) {
        return new StatisticsPeriod(range, startDate, endDate, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
    }

    private User requireLocalUser() {
        User user = userMapper.selectEnabledUserByCode(LOCAL_DEFAULT_USER_CODE);
        if (user == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "本地用户不存在或不可用");
        }
        return user;
    }

    private LearningStatisticsOverviewVO toOverview(LearningStatisticsOverviewRow row, long confirmedErrorCount) {
        return new LearningStatisticsOverviewVO(
                zeroIfNull(row.answerCount()),
                zeroIfNull(row.reviewedAnswerCount()),
                row.averageTotalScore(),
                confirmedErrorCount
        );
    }

    private List<LearningStatisticsDailyTrendVO> fillDailyTrends(
            StatisticsPeriod period,
            List<LearningStatisticsDailyTrendRow> rows
    ) {
        Map<LocalDate, LearningStatisticsDailyTrendRow> rowsByDay = new HashMap<>();
        rows.forEach(row -> rowsByDay.put(row.day(), row));
        return period.startDate().datesUntil(period.endDate().plusDays(1))
                .map(day -> {
                    LearningStatisticsDailyTrendRow row = rowsByDay.get(day);
                    return new LearningStatisticsDailyTrendVO(
                            day,
                            row == null ? 0L : zeroIfNull(row.answerCount()),
                            row == null ? null : row.averageTotalScore()
                    );
                })
                .toList();
    }

    private LearningStatisticsScoreDimensionsVO toScoreDimensions(LearningStatisticsScoreDimensionsRow row) {
        return new LearningStatisticsScoreDimensionsVO(
                row.grammarVocabularyScore(),
                row.naturalFluencyScore(),
                row.scenarioAdaptationScore(),
                row.informationCompletenessScore()
        );
    }

    private LearningStatisticsWeaknessVO toWeakness(LearningStatisticsWeaknessRow row, LocalDateTime now) {
        return new LearningStatisticsWeaknessVO(
                row.userErrorTypeId(),
                row.userErrorTypeName(),
                row.userErrorTypeStatus(),
                row.errorTypeId(),
                row.errorTypeCode(),
                row.errorTypeName(),
                zeroIfNull(row.confirmedCount()),
                zeroIfNull(row.lowSeverityCount()),
                zeroIfNull(row.mediumSeverityCount()),
                zeroIfNull(row.highSeverityCount()),
                row.lastConfirmedAt(),
                resolveReviewState(row.reviewCardStatus(), row.reviewCardDueAt(), now)
        );
    }

    private String resolveReviewState(String reviewCardStatus, LocalDateTime dueAt, LocalDateTime now) {
        if (reviewCardStatus == null) {
            return "NOT_CREATED";
        }
        if (CARD_MASTERED.equals(reviewCardStatus)) {
            return CARD_MASTERED;
        }
        if (CARD_ACTIVE.equals(reviewCardStatus) && dueAt != null && !dueAt.isAfter(now)) {
            return "DUE";
        }
        return CARD_ACTIVE;
    }

    private LearningStatisticsReviewOverviewVO toReviewOverview(
            LearningStatisticsCurrentReviewRow currentReview,
            LearningStatisticsPeriodReviewRow periodReview,
            long completedCycleCount
    ) {
        long attemptCount = zeroIfNull(periodReview.reviewAttemptCount());
        long passCount = zeroIfNull(periodReview.reviewPassCount());
        BigDecimal passRate = attemptCount == 0
                ? null
                : BigDecimal.valueOf(passCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(attemptCount), 2, RoundingMode.HALF_UP);
        return new LearningStatisticsReviewOverviewVO(
                zeroIfNull(currentReview.dueCardCount()),
                zeroIfNull(currentReview.activeCardCount()),
                zeroIfNull(currentReview.masteredCardCount()),
                attemptCount,
                passCount,
                passRate,
                completedCycleCount
        );
    }

    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private record StatisticsPeriod(
            String range,
            LocalDate startDate,
            LocalDate endDate,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }
}
