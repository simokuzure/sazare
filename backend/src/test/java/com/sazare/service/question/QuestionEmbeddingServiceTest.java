package com.sazare.service.question;

import com.sazare.entity.QuestionEmbeddingCandidate;
import com.sazare.entity.Question;
import com.sazare.mapper.QuestionEmbeddingMapper;
import com.sazare.service.ai.AiEmbeddingClient;
import com.sazare.vo.QuestionEmbeddingBackfillVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionEmbeddingServiceTest {

    @Test
    void backfillShouldOnlySaveStaleRegularQuestionEmbeddings() {
        QuestionEmbeddingMapper mapper = mock(QuestionEmbeddingMapper.class);
        AiEmbeddingClient client = mock(AiEmbeddingClient.class);
        when(client.modelName()).thenReturn("gemini-embedding-001");
        when(client.embed(anyString())).thenReturn(vector(0.25f));
        QuestionEmbeddingService service = new QuestionEmbeddingService(mapper, client);

        QuestionEmbeddingCandidate stale = candidate(100L, null, null);
        QuestionEmbeddingCandidate current = candidate(
                101L,
                service.contentHash("TRANSLATION_ZH_TO_JA", "请告诉我车站在哪里。"),
                "gemini-embedding-001"
        );
        when(mapper.selectRegularQuestionEmbeddingCandidates()).thenReturn(List.of(stale, current));

        QuestionEmbeddingBackfillVO result = service.backfill(100);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.remainingCount()).isZero();
        verify(mapper, times(1)).upsertQuestionEmbedding(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void synchronizeEmbeddingShouldUseOnlyShortQuestionSourceText() {
        QuestionEmbeddingMapper mapper = mock(QuestionEmbeddingMapper.class);
        AiEmbeddingClient client = mock(AiEmbeddingClient.class);
        when(client.modelName()).thenReturn("gemini-embedding-001");
        when(client.embed(anyString())).thenReturn(vector(0.25f));
        QuestionEmbeddingService service = new QuestionEmbeddingService(mapper, client);
        Question question = new Question();
        question.setId(199L);
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("  请告诉我车站\n在哪里。  ");
        question.setContextText("不应进入短句向量的语境。");

        service.synchronizeEmbedding(question);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).embed(contentCaptor.capture());
        assertThat(contentCaptor.getValue())
                .isEqualTo("请告诉我车站 在哪里。")
                .doesNotContain("题目原文", "语境");
        verify(mapper).upsertQuestionEmbedding(
                any(),
                anyString(),
                eq(service.contentHash(
                        "TRANSLATION_ZH_TO_JA",
                        question.getSourceText()
                )),
                anyString(),
                any()
        );
    }

    @Test
    void isSimilarShouldUseCosineThreshold() {
        QuestionEmbeddingService service = new QuestionEmbeddingService(mock(QuestionEmbeddingMapper.class), mock(AiEmbeddingClient.class));

        assertThat(service.isSimilar(vector(1f), vector(1f))).isTrue();
        assertThat(service.isSimilar(vector(1f), cosineVector(0.8f))).isTrue();
        assertThat(service.isSimilar(vector(1f), cosineVector(0.79f))).isFalse();
        assertThat(service.isSimilar(vector(1f), vectorAt(1, 1f))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRANSLATION_ZH_TO_JA_ARTICLE", "TRANSLATION_EN_TO_JA_ARTICLE"})
    void synchronizeEmbeddingShouldUseOnlyArticleBody(String questionType) {
        QuestionEmbeddingMapper mapper = mock(QuestionEmbeddingMapper.class);
        AiEmbeddingClient client = mock(AiEmbeddingClient.class);
        when(client.modelName()).thenReturn("gemini-embedding-001");
        when(client.embed(anyString())).thenReturn(vector(0.25f));
        QuestionEmbeddingService service = new QuestionEmbeddingService(mapper, client);
        Question article = new Question();
        article.setId(200L);
        article.setQuestionType(questionType);
        article.setSourceText("文章正文。\n\n第二句正文。");
        article.setContextText("不应进入文章向量的语境。");

        service.synchronizeEmbedding(article);

        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).embed(contentCaptor.capture());
        assertThat(contentCaptor.getValue())
                .isEqualTo("文章正文：文章正文。 第二句正文。")
                .doesNotContain("语境");
    }

    @Test
    void backfillShouldTreatLegacyArticleEmbeddingAsStale() {
        QuestionEmbeddingMapper mapper = mock(QuestionEmbeddingMapper.class);
        AiEmbeddingClient client = mock(AiEmbeddingClient.class);
        when(client.modelName()).thenReturn("gemini-embedding-001");
        when(client.embed(anyString())).thenReturn(vector(0.25f));
        QuestionEmbeddingService service = new QuestionEmbeddingService(mapper, client);
        QuestionEmbeddingCandidate article = candidate(
                201L,
                "legacy-content-hash",
                "gemini-embedding-001"
        );
        article.setQuestionType("TRANSLATION_ZH_TO_JA_ARTICLE");
        article.setSourceText("文章正文。");
        article.setContextText("旧语境。");
        when(mapper.selectRegularQuestionEmbeddingCandidates()).thenReturn(List.of(article));

        QuestionEmbeddingBackfillVO result = service.backfill(100);

        assertThat(result.processedCount()).isEqualTo(1);
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).embed(contentCaptor.capture());
        assertThat(contentCaptor.getValue())
                .isEqualTo("文章正文：文章正文。")
                .doesNotContain("旧语境");
        verify(mapper).upsertQuestionEmbedding(any(), anyString(), anyString(), anyString(), any());
    }

    private QuestionEmbeddingCandidate candidate(Long id, String contentHash, String modelName) {
        QuestionEmbeddingCandidate candidate = new QuestionEmbeddingCandidate();
        candidate.setQuestionId(id);
        candidate.setQuestionType("TRANSLATION_ZH_TO_JA");
        candidate.setSourceText("请告诉我车站在哪里。");
        candidate.setContextText("问路场景。");
        candidate.setContentHash(contentHash);
        candidate.setModelName(modelName);
        return candidate;
    }

    private List<Float> vector(float firstValue) {
        return vectorAt(0, firstValue);
    }

    private List<Float> vectorAt(int index, float value) {
        Float[] values = new Float[768];
        java.util.Arrays.fill(values, 0f);
        values[index] = value;
        return java.util.Arrays.asList(values);
    }

    private List<Float> cosineVector(float similarity) {
        Float[] values = new Float[768];
        java.util.Arrays.fill(values, 0f);
        values[0] = similarity;
        values[1] = (float) Math.sqrt(1 - similarity * similarity);
        return java.util.Arrays.asList(values);
    }
}
