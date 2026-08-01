package com.jt.learning.service.impl;

import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.entity.User;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerListItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAnswerServiceImplTest {

    private UserMapper userMapper;
    private UserAnswerMapper userAnswerMapper;
    private UserAnswerServiceImpl userAnswerService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userAnswerMapper = mock(UserAnswerMapper.class);
        userAnswerService = new UserAnswerServiceImpl(userMapper, userAnswerMapper);
    }

    @Test
    void listUserAnswersShouldReturnPagedRecords() {
        User user = localUser();
        UserAnswerListItemRow row = userAnswerRow();
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.countUserAnswers(eq(1L), any())).thenReturn(1L);
        when(userAnswerMapper.selectUserAnswerList(eq(1L), any(), eq(20), eq(0L))).thenReturn(List.of(row));

        PageVO<UserAnswerListItemVO> page = userAnswerService.listUserAnswers(new UserAnswerQueryRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.items()).hasSize(1);
        UserAnswerListItemVO item = page.items().getFirst();
        assertThat(item.id()).isEqualTo(10L);
        assertThat(item.questionId()).isEqualTo(100L);
        assertThat(item.questionType()).isEqualTo("TRANSLATION_ZH_TO_JA");
        assertThat(item.sourceText()).isEqualTo("我明天下午要去银行办理转账。");
        assertThat(item.level()).isEqualTo("N4");
        assertThat(item.difficulty()).isEqualTo(3);
        assertThat(item.answerStatus()).isEqualTo("REVIEWED");
        assertThat(item.scores().grammarVocabularyScore()).isEqualTo(90);
        assertThat(item.totalScore()).isEqualByComparingTo("88.75");
        assertThat(item.overallComment()).isEqualTo("整体表达自然，信息完整。");
    }

    @Test
    void listUserAnswersShouldPassFiltersAndPaginationToMapper() {
        User user = localUser();
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.countUserAnswers(eq(1L), any())).thenReturn(2L);
        when(userAnswerMapper.selectUserAnswerList(eq(1L), any(), eq(10), eq(20L))).thenReturn(List.of());

        userAnswerService.listUserAnswers(new UserAnswerQueryRequest(
                " REVIEWED ",
                100L,
                " N4 ",
                new BigDecimal("60"),
                new BigDecimal("90"),
                3,
                10
        ));

        ArgumentCaptor<UserAnswerQueryRequest> requestCaptor = ArgumentCaptor.forClass(UserAnswerQueryRequest.class);
        verify(userAnswerMapper).countUserAnswers(eq(1L), requestCaptor.capture());
        UserAnswerQueryRequest request = requestCaptor.getValue();
        assertThat(request.answerStatus()).isEqualTo("REVIEWED");
        assertThat(request.questionId()).isEqualTo(100L);
        assertThat(request.level()).isEqualTo("N4");
        assertThat(request.minTotalScore()).isEqualByComparingTo("60");
        assertThat(request.maxTotalScore()).isEqualByComparingTo("90");
        verify(userAnswerMapper).selectUserAnswerList(eq(1L), any(), eq(10), eq(20L));
    }

    @Test
    void listUserAnswersShouldReturnEmptyPageWhenTotalIsZero() {
        User user = localUser();
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.countUserAnswers(eq(1L), any())).thenReturn(0L);

        PageVO<UserAnswerListItemVO> page = userAnswerService.listUserAnswers(new UserAnswerQueryRequest(
                "FAILED",
                null,
                null,
                null,
                null,
                2,
                20
        ));

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(20);
        verify(userAnswerMapper, never()).selectUserAnswerList(any(), any(), any(Integer.class), any(Long.class));
    }

    @Test
    void listUserAnswersShouldRejectInvalidScoreRange() {
        assertThatThrownBy(() -> userAnswerService.listUserAnswers(new UserAnswerQueryRequest(
                null,
                null,
                null,
                new BigDecimal("90"),
                new BigDecimal("60"),
                1,
                20
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minTotalScore 不能大于 maxTotalScore");

        verify(userMapper, never()).selectEnabledUserByCode(any());
    }

    @Test
    void listUserAnswersShouldRejectMissingLocalUser() {
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(null);

        assertThatThrownBy(() -> userAnswerService.listUserAnswers(new UserAnswerQueryRequest(
                null,
                null,
                null,
                null,
                null,
                1,
                20
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地用户不存在或不可用");

        verify(userAnswerMapper, never()).countUserAnswers(any(), any());
    }

    private User localUser() {
        User user = new User();
        user.setId(1L);
        user.setUserCode("LOCAL_DEFAULT");
        user.setNickname("本地用户");
        user.setUserType("LOCAL");
        user.setEnabled(true);
        user.setDeleted(false);
        return user;
    }

    private UserAnswerListItemRow userAnswerRow() {
        UserAnswerListItemRow row = new UserAnswerListItemRow();
        row.setId(10L);
        row.setQuestionId(100L);
        row.setQuestionType("TRANSLATION_ZH_TO_JA");
        row.setSourceText("我明天下午要去银行办理转账。");
        row.setLevel("N4");
        row.setDifficulty(3);
        row.setAnswerText("明日の午後、銀行へ振り込みに行きます。");
        row.setAnswerStatus("REVIEWED");
        row.setGrammarVocabularyScore(90);
        row.setNaturalFluencyScore(85);
        row.setScenarioAdaptationScore(88);
        row.setInformationCompletenessScore(92);
        row.setTotalScore(new BigDecimal("88.75"));
        row.setOverallComment("整体表达自然，信息完整。");
        row.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 30));
        row.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 10, 30, 5));
        return row;
    }
}
