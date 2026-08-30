package com.sazare.service;

import com.sazare.entity.Question;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewQuestionTagWriterTest {

    private QuestionMapper questionMapper;
    private QuestionTagMapper questionTagMapper;
    private ReviewQuestionTagWriter writer;

    @BeforeEach
    void setUp() {
        questionMapper = mock(QuestionMapper.class);
        questionTagMapper = mock(QuestionTagMapper.class);
        writer = new ReviewQuestionTagWriter(questionMapper, questionTagMapper);
    }

    @Test
    void saveShouldUseTransactionAndWriteDistinctTags() throws NoSuchMethodException {
        Method method = ReviewQuestionTagWriter.class.getMethod("saveIfUntagged", Long.class, List.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
        when(questionMapper.selectQuestionForUpdateById(500L)).thenReturn(new Question());
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(0);

        boolean saved = writer.saveIfUntagged(500L, List.of(10L, 11L, 10L));

        assertThat(saved).isTrue();
        verify(questionTagMapper).insertQuestionTag(500L, 10L);
        verify(questionTagMapper).insertQuestionTag(500L, 11L);
    }

    @Test
    void saveShouldNotOverwriteExistingTags() {
        when(questionMapper.selectQuestionForUpdateById(500L)).thenReturn(new Question());
        when(questionTagMapper.countByQuestionId(500L)).thenReturn(1);

        boolean saved = writer.saveIfUntagged(500L, List.of(10L));

        assertThat(saved).isFalse();
        verify(questionTagMapper, never()).insertQuestionTag(500L, 10L);
    }

    @Test
    void saveShouldSkipDeletedQuestion() {
        when(questionMapper.selectQuestionForUpdateById(500L)).thenReturn(null);

        boolean saved = writer.saveIfUntagged(500L, List.of(10L));

        assertThat(saved).isFalse();
        verify(questionTagMapper, never()).countByQuestionId(500L);
    }
}
