package com.sazare.service.impl;

import com.sazare.dto.LearningStatisticsCurrentReviewRow;
import com.sazare.dto.LearningStatisticsDailyTrendRow;
import com.sazare.dto.LearningStatisticsOverviewRow;
import com.sazare.dto.LearningStatisticsPeriodReviewRow;
import com.sazare.dto.LearningStatisticsQueryRequest;
import com.sazare.dto.LearningStatisticsScoreDimensionsRow;
import com.sazare.entity.User;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.LearningStatisticsMapper;
import com.sazare.mapper.UserMapper;
import com.sazare.service.LearningStatisticsService;
import com.sazare.vo.LearningStatisticsCheckInOverviewVO;
import com.sazare.vo.LearningStatisticsDailyTrendVO;
import com.sazare.vo.LearningStatisticsPeriodVO;
import com.sazare.vo.LearningStatisticsPracticeVO;
import com.sazare.vo.LearningStatisticsReviewOverviewVO;
import com.sazare.vo.LearningStatisticsScoreDimensionsVO;
import com.sazare.vo.LearningStatisticsVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LearningStatisticsServiceImpl implements LearningStatisticsService {

    private static final String LOCAL_DEFAULT_USER_CODE = "LOCAL_DEFAULT";
    private static final String RANGE_LAST_7_DAYS = "LAST_7_DAYS";
    private static final String RANGE_LAST_30_DAYS = "LAST_30_DAYS";
    private static final String RANGE_LAST_90_DAYS = "LAST_90_DAYS";
    private static final String RANGE_CUSTOM = "CUSTOM";
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
                user.getId(), request.learningMode(), period.startAt(), period.endAt());
        LearningStatisticsOverviewRow correctionOverview = learningStatisticsMapper.selectCorrectionOverview(
                user.getId(), request.learningMode(), period.startAt(), period.endAt());
        LearningStatisticsScoreDimensionsRow scoreDimensions = learningStatisticsMapper.selectScoreDimensions(
                user.getId(), request.learningMode(), period.startAt(), period.endAt());
        LearningStatisticsScoreDimensionsRow correctionScoreDimensions =
                learningStatisticsMapper.selectCorrectionScoreDimensions(
                        user.getId(), request.learningMode(), period.startAt(), period.endAt());
        List<LearningStatisticsDailyTrendVO> dailyTrends = fillDailyTrends(
                period, learningStatisticsMapper.selectDailyTrends(
                        user.getId(), request.learningMode(), period.startAt(), period.endAt()));
        List<LearningStatisticsDailyTrendVO> correctionDailyTrends = fillDailyTrends(
                period, learningStatisticsMapper.selectCorrectionDailyTrends(
                        user.getId(), request.learningMode(), period.startAt(), period.endAt()));
        LearningStatisticsCurrentReviewRow currentReview = learningStatisticsMapper.selectCurrentReviewSummary(
                user.getId(), request.learningMode(), LocalDateTime.now(learningStatisticsClock));
        LearningStatisticsPeriodReviewRow periodReview = learningStatisticsMapper.selectPeriodReviewSummary(
                user.getId(), request.learningMode(), period.startAt(), period.endAt());

        return new LearningStatisticsVO(
                toCheckInOverview(learningStatisticsMapper.selectLearningActivityDates(user.getId())),
                new LearningStatisticsPeriodVO(period.range(), period.startDate(), period.endDate()),
                toPractice(overview, dailyTrends, scoreDimensions),
                toPractice(correctionOverview, correctionDailyTrends, correctionScoreDimensions),
                toReviewOverview(currentReview, periodReview)
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

    private LearningStatisticsCheckInOverviewVO toCheckInOverview(List<LocalDate> activityDates) {
        List<LocalDate> dates = activityDates == null
                ? List.of()
                : activityDates.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .toList();
        if (dates.isEmpty()) {
            return new LearningStatisticsCheckInOverviewVO(0L, 0L);
        }

        LocalDate today = LocalDate.now(learningStatisticsClock);
        LocalDate latestActivityDate = dates.getFirst();
        if (latestActivityDate.isBefore(today.minusDays(1))) {
            return new LearningStatisticsCheckInOverviewVO(0L, (long) dates.size());
        }

        long currentStreakDays = 0L;
        LocalDate expectedDate = latestActivityDate;
        for (LocalDate activityDate : dates) {
            if (!activityDate.equals(expectedDate)) {
                break;
            }
            currentStreakDays++;
            expectedDate = expectedDate.minusDays(1);
        }
        return new LearningStatisticsCheckInOverviewVO(currentStreakDays, (long) dates.size());
    }

    private LearningStatisticsPracticeVO toPractice(
            LearningStatisticsOverviewRow overview,
            List<LearningStatisticsDailyTrendVO> dailyTrends,
            LearningStatisticsScoreDimensionsRow scoreDimensions
    ) {
        return new LearningStatisticsPracticeVO(
                overview == null ? 0L : zeroIfNull(overview.attemptCount()),
                overview == null ? null : overview.averageTotalScore(),
                dailyTrends,
                toScoreDimensions(scoreDimensions)
        );
    }

    private List<LearningStatisticsDailyTrendVO> fillDailyTrends(
            StatisticsPeriod period,
            List<LearningStatisticsDailyTrendRow> rows
    ) {
        Map<LocalDate, LearningStatisticsDailyTrendRow> rowsByDay = new HashMap<>();
        if (rows != null) {
            rows.forEach(row -> rowsByDay.put(row.day(), row));
        }
        return period.startDate().datesUntil(period.endDate().plusDays(1))
                .map(day -> {
                    LearningStatisticsDailyTrendRow row = rowsByDay.get(day);
                    return new LearningStatisticsDailyTrendVO(
                        day,
                        row == null ? 0L : zeroIfNull(row.attemptCount()),
                        row == null ? null : row.averageTotalScore()
                    );
                })
                .toList();
    }

    private LearningStatisticsScoreDimensionsVO toScoreDimensions(LearningStatisticsScoreDimensionsRow row) {
        if (row == null) {
            return new LearningStatisticsScoreDimensionsVO(null, null, null, null);
        }
        return new LearningStatisticsScoreDimensionsVO(
                row.grammarVocabularyScore(),
                row.naturalFluencyScore(),
                row.scenarioAdaptationScore(),
                row.informationCompletenessScore()
        );
    }

    private LearningStatisticsReviewOverviewVO toReviewOverview(
            LearningStatisticsCurrentReviewRow currentReview,
            LearningStatisticsPeriodReviewRow periodReview
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
                zeroIfNull(currentReview.inProgressCardCount()),
                zeroIfNull(currentReview.masteredCardCount()),
                attemptCount,
                passRate
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
