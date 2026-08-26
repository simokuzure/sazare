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
import com.jt.learning.mapper.LearningStatisticsMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.vo.LearningStatisticsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningStatisticsServiceImplTest {

    private UserMapper userMapper;
    private LearningStatisticsMapper learningStatisticsMapper;
    private LearningStatisticsServiceImpl service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        learningStatisticsMapper = mock(LearningStatisticsMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-09T03:00:00Z"), ZoneId.of("Asia/Tokyo"));
        service = new LearningStatisticsServiceImpl(userMapper, learningStatisticsMapper, clock);
    }

    @Test
    void shouldUseTokyoThirtyDayWindowAndFillMissingTrendDays() {
        stubLocalUser();
        stubStatisticsRows();
        when(learningStatisticsMapper.selectDailyTrends(eq(1L), eq("ZH_TO_JA"), any(), any()))
                .thenReturn(List.of(new LearningStatisticsDailyTrendRow(
                        LocalDate.of(2026, 8, 9), 2L, new BigDecimal("82.50"))));

        LearningStatisticsVO result = service.getLearningStatistics(new LearningStatisticsQueryRequest(null, null, null));

        ArgumentCaptor<LocalDateTime> startAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(learningStatisticsMapper).selectOverview(eq(1L), eq("ZH_TO_JA"), startAtCaptor.capture(), endAtCaptor.capture());
        assertThat(startAtCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 7, 11, 0, 0));
        assertThat(endAtCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 10, 0, 0));
        assertThat(result.dailyTrends()).hasSize(30);
        assertThat(result.dailyTrends().getFirst().averageTotalScore()).isNull();
        assertThat(result.dailyTrends().getLast().answerCount()).isEqualTo(2);
    }

    @Test
    void shouldRejectIncompleteOrReversedCustomPeriodBeforeLoadingUser() {
        assertThatThrownBy(() -> service.getLearningStatistics(new LearningStatisticsQueryRequest(
                "CUSTOM", LocalDate.of(2026, 8, 2), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须同时提供");
        assertThatThrownBy(() -> service.getLearningStatistics(new LearningStatisticsQueryRequest(
                "CUSTOM", LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能晚于");

        verify(userMapper, never()).selectEnabledUserByCode(any());
    }

    @Test
    void shouldCalculateReviewRateAndExposeConfirmedWeaknessOnly() {
        stubLocalUser();
        stubStatisticsRows();
        when(learningStatisticsMapper.countConfirmedErrors(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(3L);
        when(learningStatisticsMapper.selectTopWeaknesses(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(List.of(
                new LearningStatisticsWeaknessRow(
                        10L, "交通工具に間に合う", "ACTIVE", 20L, "PARTICLE", "助词错误",
                        3L, 1L, 1L, 1L, LocalDateTime.of(2026, 8, 8, 12, 0), "ACTIVE",
                        LocalDateTime.of(2026, 8, 9, 10, 0)
                )
        ));
        when(learningStatisticsMapper.selectPeriodReviewSummary(eq(1L), eq("ZH_TO_JA"), any(), any()))
                .thenReturn(new LearningStatisticsPeriodReviewRow(4L, 3L));

        LearningStatisticsVO result = service.getLearningStatistics(new LearningStatisticsQueryRequest(
                "CUSTOM", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 9)));

        assertThat(result.overview().confirmedErrorCount()).isEqualTo(3);
        assertThat(result.weaknesses()).singleElement().satisfies(item -> {
            assertThat(item.userErrorTypeName()).isEqualTo("交通工具に間に合う");
            assertThat(item.reviewState()).isEqualTo("DUE");
        });
        assertThat(result.reviewOverview().periodReviewPassRate()).isEqualByComparingTo("75.00");
    }

    private void stubLocalUser() {
        User user = new User();
        user.setId(1L);
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
    }

    private void stubStatisticsRows() {
        when(learningStatisticsMapper.selectOverview(eq(1L), eq("ZH_TO_JA"), any(), any()))
                .thenReturn(new LearningStatisticsOverviewRow(4L, 3L, new BigDecimal("82.50")));
        when(learningStatisticsMapper.countConfirmedErrors(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(0L);
        when(learningStatisticsMapper.selectDailyTrends(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(List.of());
        when(learningStatisticsMapper.selectScoreDimensions(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(
                new LearningStatisticsScoreDimensionsRow(
                        new BigDecimal("80"), new BigDecimal("81"), new BigDecimal("82"), new BigDecimal("83")));
        when(learningStatisticsMapper.selectTopWeaknesses(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(List.of());
        when(learningStatisticsMapper.selectCurrentReviewSummary(eq(1L), eq("ZH_TO_JA"), any())).thenReturn(
                new LearningStatisticsCurrentReviewRow(1L, 2L, 3L));
        when(learningStatisticsMapper.selectPeriodReviewSummary(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(
                new LearningStatisticsPeriodReviewRow(0L, 0L));
        when(learningStatisticsMapper.countCompletedReviewCycles(eq(1L), eq("ZH_TO_JA"), any(), any())).thenReturn(1L);
    }
}
