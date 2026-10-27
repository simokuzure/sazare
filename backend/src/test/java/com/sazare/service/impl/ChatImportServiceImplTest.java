package com.sazare.service.impl;

import com.sazare.dto.ChatImportConfirmationRequest;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.AiChatImportAnalysisDTO;
import com.sazare.entity.*;
import com.sazare.mapper.*;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.ReviewService;
import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.prompt.AiChatImportPromptBuilder;
import com.sazare.service.ai.validation.ChatImportAiResponseValidator;
import com.sazare.util.ChatMarkdownParser;
import com.sazare.vo.ChatImportAnalysisVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatImportServiceImplTest {
    private final UserMapper users = mock(UserMapper.class);
    private final ErrorTypeMapper errors = mock(ErrorTypeMapper.class);
    private final UserErrorTypeMapper types = mock(UserErrorTypeMapper.class);
    private final QuestionMapper questions = mock(QuestionMapper.class);
    private final QuestionAnswerMapper answers = mock(QuestionAnswerMapper.class);
    private final ReviewCardMapper cards = mock(ReviewCardMapper.class);
    private final ReviewService reviews = mock(ReviewService.class);
    private final AiChatImportClient ai = mock(AiChatImportClient.class);
    private final DictionaryCacheService dictionary = mock(DictionaryCacheService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatImportServiceImpl service = new ChatImportServiceImpl(new ChatMarkdownParser(), dictionary,
            new AiChatImportPromptBuilder(mapper), ai, new ChatImportAiResponseValidator(mapper),
            users, errors, types, questions, answers, cards, reviews);

    @BeforeEach
    void setup() {
        User user = new User(); user.setId(1L);
        when(users.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        UserErrorType type = new UserErrorType(); type.setId(2L); type.setErrorTypeId(3L);
        type.setLearningMode("ZH_TO_JA"); type.setName("助词");
        when(types.selectActiveByIdAndUserId(2L, 1L)).thenReturn(type);
        ErrorType error = new ErrorType(); error.setCode("PARTICLE");
        when(errors.selectEnabledLeafById(3L)).thenReturn(error);
        ReviewCard card = new ReviewCard(); card.setId(4L); card.setUserId(1L); card.setStatus("ACTIVE");
        when(cards.selectForUpdateByUserErrorTypeId(2L)).thenReturn(card);
        when(questions.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0); question.setId(5L); return 1;
        });
        when(reviews.recordChatImportErrors(eq(1L), anyList(), eq(2L), anyString(), any())).thenReturn(card);
    }

    @Test
    void analysisSendsAllMessagesOnceAndDoesNotWriteBusinessData() {
        when(dictionary.getEnabledLeafErrorTypes()).thenReturn(List.of(
                new AiErrorTypeOptionDTO(3L, "PARTICLE", "助词", "助词说明", "GRAMMAR", "语法")));
        var expected = new ChatImportAnalysisVO(List.of(1, 3), List.of());
        when(ai.analyze(any())).thenReturn(mapper.writeValueAsString(new AiChatImportAnalysisDTO(List.of(
                new AiChatImportAnalysisDTO.MessageCheck(1, List.of()),
                new AiChatImportAnalysisDTO.MessageCheck(3, List.of())), List.of())));
        byte[] markdown = "## User\n日本に来た前に、何を勉強しましたか。\n## Assistant\n日本語だよ。\n## User\nありがとう。"
                .getBytes(StandardCharsets.UTF_8);

        assertThat(service.analyze(markdown, "ZH_TO_JA")).isEqualTo(expected);

        var prompt = ArgumentCaptor.forClass(AiQuestionPrompt.class);
        verify(ai, times(1)).analyze(prompt.capture());
        var input = mapper.readTree(prompt.getValue().userPrompt());
        assertThat(input.get("learningMode").asString()).isEqualTo("ZH_TO_JA");
        var messages = input.get("messages");
        assertThat(messages.size()).isEqualTo(3);
        assertThat(messages.get(0).get("content").asString()).isEqualTo("日本に来た前に、何を勉強しましたか。");
        assertThat(messages.get(1).get("role").asString()).isEqualTo("assistant");
        assertThat(messages.get(1).get("content").asString()).isEqualTo("日本語だよ。");
        assertThat(messages.get(2).get("id").asInt()).isEqualTo(3);
        assertThat(messages.get(2).get("content").asString()).isEqualTo("ありがとう。");
        verifyNoInteractions(questions, answers, types, cards, reviews, errors);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void incompleteAnalysisLogsFullReplyBeforeFailingWithoutRetryOrBusinessWrites(CapturedOutput output) {
        when(dictionary.getEnabledLeafErrorTypes()).thenReturn(List.of(
                new AiErrorTypeOptionDTO(3L, "PARTICLE", "助词", "助词说明", "GRAMMAR", "语法")));
        String reply = mapper.writeValueAsString(new AiChatImportAnalysisDTO(
                List.of(new AiChatImportAnalysisDTO.MessageCheck(1, List.of())), List.of()));
        when(ai.analyze(any())).thenReturn(reply);
        byte[] markdown = "## User\nこんにちは。\n## Assistant\nこんにちは。\n## User\nまたね。"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.analyze(markdown, "ZH_TO_JA"))
                .hasMessageContaining("不完整或不合法", "消息检查记录数量不符，应为 2 条，实际为 1 条");

        String logOutput = output.getAll();
        assertThat(logOutput).contains("聊天分析 AI 原始回复: promptVersion=" + AiChatImportPromptBuilder.VERSION
                + ", learningMode=ZH_TO_JA, response=" + reply);
        assertThat(logOutput.indexOf("聊天分析 AI 原始回复")).isLessThan(logOutput.indexOf("聊天分析结果校验失败"));

        verify(ai, times(1)).analyze(any());
        verifyNoInteractions(questions, answers, types, cards, reviews, errors);
    }

    @Test
    void groupsCandidatesReusesQuestionAndTakesHighestSeverity() {
        var request = new ChatImportConfirmationRequest("ZH_TO_JA", List.of(item("LOW"), item("HIGH")));
        var result = service.confirm(request);
        assertThat(result.cards()).hasSize(1);
        assertThat(result.cards().getFirst().action()).isEqualTo("UPDATED");
        verify(questions, times(1)).insertQuestion(any());
        verify(answers, times(1)).insertQuestionAnswer(any());
        verify(reviews).recordChatImportErrors(eq(1L), eq(List.of(5L)), eq(2L), eq("HIGH"), any());
        verifyNoInteractions(ai, dictionary);
    }

    @Test
    void confirmationKeepsKanaHintsInReviewSource() {
        String source = "他仍然在袒护芙丽达。（人名读音：フリーダ。）";
        var item = new ChatImportConfirmationRequest.Item("EXISTING_USER_ERROR_TYPE", null, 2L,
                null, null, "LOW", source, "彼は今もフリーダをかばっている。");
        service.confirm(new ChatImportConfirmationRequest("ZH_TO_JA", List.of(item)));
        var question = ArgumentCaptor.forClass(Question.class);
        verify(questions).insertQuestion(question.capture());
        assertThat(question.getValue().getSourceText()).isEqualTo(source);
    }

    @Test
    void repeatedConfirmationsAreNotDeduplicated() {
        var request = new ChatImportConfirmationRequest("ZH_TO_JA", List.of(item("MEDIUM")));
        service.confirm(request);
        service.confirm(request);
        verify(reviews, times(2)).recordChatImportErrors(any(), anyList(), any(), anyString(), any());
        verify(questions, times(2)).insertQuestion(any());
    }

    @Test
    void rejectsDifferentLearningModeAndMissingCardBeforeWritingQuestions() {
        var englishItem = new ChatImportConfirmationRequest.Item("EXISTING_USER_ERROR_TYPE", null, 2L,
                null, null, "LOW", "I went to school.", "学校に行きました。");
        assertThatThrownBy(() -> service.confirm(new ChatImportConfirmationRequest("EN_TO_JA", List.of(englishItem))))
                .hasMessageContaining("学习模式");
        when(cards.selectForUpdateByUserErrorTypeId(2L)).thenReturn(null);
        assertThatThrownBy(() -> service.confirm(new ChatImportConfirmationRequest("ZH_TO_JA", List.of(item("LOW")))))
                .hasMessageContaining("卡片不存在");
        verifyNoInteractions(questions, answers, reviews);
    }

    @Test
    void transactionProxyRollsBackWhenReviewWriteFails() {
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status = mock(org.springframework.transaction.TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(status);
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var proxy = (com.sazare.service.ChatImportService) factory.getProxy();
        when(reviews.recordChatImportErrors(any(), anyList(), any(), anyString(), any()))
                .thenThrow(new com.sazare.exception.BusinessException(com.sazare.exception.ErrorCode.BUSINESS_ERROR, "写入失败"));
        assertThatThrownBy(() -> proxy.confirm(new ChatImportConfirmationRequest("ZH_TO_JA", List.of(item("LOW")))))
                .hasMessage("写入失败");
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }

    private ChatImportConfirmationRequest.Item item(String severity) {
        return new ChatImportConfirmationRequest.Item("EXISTING_USER_ERROR_TYPE", null, 2L,
                null, null, severity, "我去了学校。", "学校に行きました。");
    }
}
