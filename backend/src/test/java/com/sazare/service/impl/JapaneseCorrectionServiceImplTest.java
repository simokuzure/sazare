package com.sazare.service.impl;

import com.sazare.dto.AiAnswerScoresDTO;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.AiJapaneseCorrectionCommentsDTO;
import com.sazare.dto.AiJapaneseCorrectionReviewDTO;
import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.entity.User;
import com.sazare.entity.UserAnswer;
import com.sazare.service.DictionaryCacheService;
import com.sazare.mapper.UserAnswerMapper;
import com.sazare.mapper.UserMapper;
import com.sazare.service.ai.AiJapaneseCorrectionClient;
import com.sazare.service.ai.prompt.AiJapaneseCorrectionPromptBuilder;
import com.sazare.service.ai.validation.JapaneseCorrectionAiResponseValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JapaneseCorrectionServiceImplTest {

    @Test
    void correctShouldNotOpenTransactionAroundAiCall() throws NoSuchMethodException {
        var method = JapaneseCorrectionServiceImpl.class.getMethod("correct", JapaneseCorrectionRequest.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void shouldSaveReviewedCorrectionWithoutQuestionAndRecalculateTotalScore() {
        UserMapper userMapper = mock(UserMapper.class);
        UserAnswerMapper userAnswerMapper = mock(UserAnswerMapper.class);
        DictionaryCacheService dictionaryCacheService = mock(DictionaryCacheService.class);
        AiJapaneseCorrectionPromptBuilder promptBuilder = mock(AiJapaneseCorrectionPromptBuilder.class);
        AiJapaneseCorrectionClient client = mock(AiJapaneseCorrectionClient.class);
        JapaneseCorrectionAiResponseValidator validator = mock(JapaneseCorrectionAiResponseValidator.class);
        JapaneseCorrectionServiceImpl service = new JapaneseCorrectionServiceImpl(
                userMapper, userAnswerMapper, dictionaryCacheService, promptBuilder, client, validator);

        User user = new User();
        user.setId(1L);
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user);
        when(dictionaryCacheService.getEnabledLeafErrorTypes()).thenReturn(List.of(
                new AiErrorTypeOptionDTO(
                        9L, "PARTICLE", "助词错误", "说明", "GRAMMAR_SYNTAX", "语法与句法"),
                new AiErrorTypeOptionDTO(
                        10L, "PUNCTUATION", "标点错误", "说明", "WRITING_FORMAT", "书写与格式")));
        var prompt = new com.sazare.service.ai.AiQuestionPrompt("system", "user");
        when(promptBuilder.build(any(), any())).thenReturn(prompt);
        when(client.correct(eq(prompt), any())).thenReturn("{}");
        when(validator.validate(eq("{}"), eq("今日は晴れです。"), anyMap())).thenReturn(
                new AiJapaneseCorrectionReviewDTO(
                        new AiAnswerScoresDTO(80, 81, 82, 83),
                        BigDecimal.ZERO,
                        "今日は晴れです。",
                        "文本自然。",
                        new AiJapaneseCorrectionCommentsDTO("正确。", "自然。", "一致。", "完整。"),
                        List.of(),
                        List.of(),
                        List.of()
                ));
        when(userAnswerMapper.insertReviewedCorrection(any())).thenAnswer(invocation -> {
            UserAnswer answer = invocation.getArgument(0);
            answer.setId(10L);
            return 1;
        });

        var result = service.correct(new JapaneseCorrectionRequest(" 今日は晴れです。 "));

        assertThat(result.userAnswerId()).isEqualTo(10L);
        assertThat(result.questionId()).isNull();
        assertThat(result.totalScore()).isEqualByComparingTo("81.50");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiErrorTypeOptionDTO>> optionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(promptBuilder).build(optionsCaptor.capture(), any());
        assertThat(optionsCaptor.getValue()).extracting(AiErrorTypeOptionDTO::code)
                .containsExactly("PARTICLE");
        ArgumentCaptor<UserAnswer> answerCaptor = ArgumentCaptor.forClass(UserAnswer.class);
        verify(userAnswerMapper).insertReviewedCorrection(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getQuestionId()).isNull();
        assertThat(answerCaptor.getValue().getAnswerStatus()).isEqualTo("REVIEWED");
        assertThat(answerCaptor.getValue().getAiRevisedText()).isEqualTo("今日は晴れです。");
        assertThat(answerCaptor.getValue().getTotalScore()).isEqualByComparingTo("81.50");
    }
}
