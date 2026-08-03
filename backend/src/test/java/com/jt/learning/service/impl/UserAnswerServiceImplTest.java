package com.jt.learning.service.impl;

import com.jt.learning.dto.UserAnswerDetailRow;
import com.jt.learning.dto.UserAnswerErrorConfirmItemRequest;
import com.jt.learning.dto.UserAnswerErrorConfirmRequest;
import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.ErrorType;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.entity.UserErrorType;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerErrorMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserErrorTypeMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.UserAnswerDetailVO;
import com.jt.learning.vo.UserAnswerErrorVO;
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
    private TagMapper tagMapper;
    private QuestionAnswerMapper questionAnswerMapper;
    private ErrorTypeMapper errorTypeMapper;
    private UserErrorTypeMapper userErrorTypeMapper;
    private UserAnswerErrorMapper userAnswerErrorMapper;
    private UserAnswerServiceImpl userAnswerService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        userAnswerMapper = mock(UserAnswerMapper.class);
        tagMapper = mock(TagMapper.class);
        questionAnswerMapper = mock(QuestionAnswerMapper.class);
        errorTypeMapper = mock(ErrorTypeMapper.class);
        userErrorTypeMapper = mock(UserErrorTypeMapper.class);
        userAnswerErrorMapper = mock(UserAnswerErrorMapper.class);
        userAnswerService = new UserAnswerServiceImpl(
                userMapper,
                userAnswerMapper,
                tagMapper,
                questionAnswerMapper,
                errorTypeMapper,
                userErrorTypeMapper,
                userAnswerErrorMapper
        );
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

    @Test
    void getUserAnswerDetailShouldReturnQuestionAnswerTagsAndReviewSummary() {
        User user = localUser();
        UserAnswerDetailRow row = userAnswerDetailRow();
        Tag tag = sceneTag();
        QuestionAnswer standardAnswer = questionAnswer(1L, "STANDARD", true, "明日の午後、銀行へ振り込みに行きます。", 0);
        QuestionAnswer referenceAnswer = questionAnswer(2L, "REFERENCE", false, "明日の午後、銀行で振込をします。", 1);
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.selectUserAnswerDetail(1L, 10L)).thenReturn(row);
        when(tagMapper.selectEnabledTagsByQuestionId(100L)).thenReturn(List.of(tag));
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(standardAnswer, referenceAnswer));

        UserAnswerDetailVO detail = userAnswerService.getUserAnswerDetail(10L);

        assertThat(detail.id()).isEqualTo(10L);
        assertThat(detail.questionId()).isEqualTo(100L);
        assertThat(detail.sourceText()).isEqualTo("我明天下午要去银行办理转账。");
        assertThat(detail.contextText()).isEqualTo("银行柜台场景。");
        assertThat(detail.grammarPoint()).isEqualTo("に行きます");
        assertThat(detail.tags()).hasSize(1);
        assertThat(detail.tags().getFirst().code()).isEqualTo("BANK");
        assertThat(detail.answers()).hasSize(2);
        assertThat(detail.answers().getFirst().answerType()).isEqualTo("STANDARD");
        assertThat(detail.answerText()).isEqualTo("明日の午後、銀行へ振り込みに行きます。");
        assertThat(detail.scores().naturalFluencyScore()).isEqualTo(85);
        assertThat(detail.totalScore()).isEqualByComparingTo("88.75");
        assertThat(detail.overallComment()).isEqualTo("整体表达自然，信息完整。");
    }

    @Test
    void getUserAnswerDetailShouldRejectMissingRecord() {
        User user = localUser();
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.selectUserAnswerDetail(1L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> userAnswerService.getUserAnswerDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("答题记录不存在或已删除");

        verify(tagMapper, never()).selectEnabledTagsByQuestionId(any());
        verify(questionAnswerMapper, never()).selectActiveAnswersByQuestionId(any());
    }

    @Test
    void getUserAnswerDetailShouldRejectMissingLocalUser() {
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(null);

        assertThatThrownBy(() -> userAnswerService.getUserAnswerDetail(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地用户不存在或不可用");

        verify(userAnswerMapper, never()).selectUserAnswerDetail(any(), any());
    }

    @Test
    void confirmUserAnswerErrorsShouldCreateUserErrorTypeAndErrorRecord() {
        User user = localUser();
        UserAnswer answer = reviewedUserAnswer();
        ErrorType errorType = enabledLeafErrorType(9L);
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.selectActiveUserAnswerById(1L, 10L)).thenReturn(answer);
        when(errorTypeMapper.selectEnabledLeafById(9L)).thenReturn(errorType);
        when(userErrorTypeMapper.insertUserErrorType(any())).thenAnswer(invocation -> {
            UserErrorType userErrorType = invocation.getArgument(0);
            userErrorType.setId(20L);
            return 1;
        });
        when(userAnswerErrorMapper.insertUserAnswerError(any())).thenAnswer(invocation -> {
            com.jt.learning.entity.UserAnswerError error = invocation.getArgument(0);
            error.setId(30L);
            return 1;
        });

        List<UserAnswerErrorVO> errors = userAnswerService.confirmUserAnswerErrors(
                10L,
                new UserAnswerErrorConfirmRequest(List.of(new UserAnswerErrorConfirmItemRequest(
                        "NEW_USER_ERROR_TYPE",
                        9L,
                        null,
                        "移动场所的助词",
                        "表示移动场所时，散步使用を而不是で。",
                        "公園で散歩します",
                        "表示移动场所时助词使用错误。",
                        "公園を散歩します。",
                        "MEDIUM",
                        0
                )))
        );

        assertThat(errors).singleElement()
                .extracting(UserAnswerErrorVO::id, UserAnswerErrorVO::errorTypeId, UserAnswerErrorVO::userErrorTypeId)
                .containsExactly(30L, 9L, 20L);
        ArgumentCaptor<com.jt.learning.entity.UserAnswerError> errorCaptor = ArgumentCaptor.forClass(
                com.jt.learning.entity.UserAnswerError.class
        );
        verify(userAnswerErrorMapper).insertUserAnswerError(errorCaptor.capture());
        assertThat(errorCaptor.getValue().getUserId()).isEqualTo(1L);
        assertThat(errorCaptor.getValue().getQuestionId()).isEqualTo(100L);
    }

    @Test
    void confirmUserAnswerErrorsShouldUseExistingUserErrorType() {
        User user = localUser();
        UserAnswer answer = reviewedUserAnswer();
        UserErrorType userErrorType = new UserErrorType();
        userErrorType.setId(20L);
        userErrorType.setUserId(1L);
        userErrorType.setErrorTypeId(9L);
        userErrorType.setStatus("ACTIVE");
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.selectActiveUserAnswerById(1L, 10L)).thenReturn(answer);
        when(userErrorTypeMapper.selectActiveByIdAndUserId(20L, 1L)).thenReturn(userErrorType);
        when(errorTypeMapper.selectEnabledLeafById(9L)).thenReturn(enabledLeafErrorType(9L));
        when(userAnswerErrorMapper.insertUserAnswerError(any())).thenReturn(1);

        userAnswerService.confirmUserAnswerErrors(
                10L,
                new UserAnswerErrorConfirmRequest(List.of(new UserAnswerErrorConfirmItemRequest(
                        "EXISTING_USER_ERROR_TYPE",
                        null,
                        20L,
                        null,
                        null,
                        "公園で散歩します",
                        "表示移动场所时助词使用错误。",
                        "公園を散歩します。",
                        "MEDIUM",
                        0
                )))
        );

        verify(userErrorTypeMapper).selectActiveByIdAndUserId(20L, 1L);
        verify(userErrorTypeMapper, never()).insertUserErrorType(any());
    }

    @Test
    void confirmUserAnswerErrorsShouldRejectUnreviewedAnswer() {
        User user = localUser();
        UserAnswer answer = reviewedUserAnswer();
        answer.setAnswerStatus("SUBMITTED");
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(userAnswerMapper.selectActiveUserAnswerById(1L, 10L)).thenReturn(answer);

        assertThatThrownBy(() -> userAnswerService.confirmUserAnswerErrors(
                10L,
                new UserAnswerErrorConfirmRequest(List.of(new UserAnswerErrorConfirmItemRequest(
                        "EXISTING_USER_ERROR_TYPE",
                        null,
                        20L,
                        null,
                        null,
                        "公園で散歩します",
                        "表示移动场所时助词使用错误。",
                        "公園を散歩します。",
                        "MEDIUM",
                        0
                )))
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅已评分的作答");

        verify(userAnswerErrorMapper, never()).insertUserAnswerError(any());
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
        row.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 30));
        row.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 10, 30, 5));
        return row;
    }

    private UserAnswerDetailRow userAnswerDetailRow() {
        UserAnswerDetailRow row = new UserAnswerDetailRow();
        row.setId(10L);
        row.setQuestionId(100L);
        row.setQuestionType("TRANSLATION_ZH_TO_JA");
        row.setSourceText("我明天下午要去银行办理转账。");
        row.setContextText("银行柜台场景。");
        row.setLevel("N4");
        row.setDifficulty(3);
        row.setGrammarPoint("に行きます");
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

    private UserAnswer reviewedUserAnswer() {
        UserAnswer answer = new UserAnswer();
        answer.setId(10L);
        answer.setUserId(1L);
        answer.setQuestionId(100L);
        answer.setAnswerText("明日の午後、公園で散歩します");
        answer.setAnswerStatus("REVIEWED");
        answer.setDeleted(false);
        return answer;
    }

    private ErrorType enabledLeafErrorType(Long id) {
        ErrorType errorType = new ErrorType();
        errorType.setId(id);
        errorType.setParentId(1L);
        errorType.setTypeLevel(2);
        errorType.setCode("PARTICLE");
        errorType.setName("助词错误");
        errorType.setEnabled(true);
        errorType.setDeleted(false);
        return errorType;
    }

    private Tag sceneTag() {
        Tag tag = new Tag();
        tag.setId(20L);
        tag.setTagType("SCENE");
        tag.setCode("BANK");
        tag.setName("银行");
        tag.setDescription("银行业务场景");
        tag.setSortOrder(1);
        return tag;
    }

    private QuestionAnswer questionAnswer(
            Long id,
            String answerType,
            boolean primaryAnswer,
            String answerText,
            int sortOrder
    ) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(id);
        answer.setQuestionId(100L);
        answer.setAnswerType(answerType);
        answer.setPrimaryAnswer(primaryAnswer);
        answer.setAnswerText(answerText);
        answer.setSortOrder(sortOrder);
        return answer;
    }
}
