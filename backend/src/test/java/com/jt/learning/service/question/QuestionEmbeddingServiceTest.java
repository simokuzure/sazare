package com.jt.learning.service.question;

import com.jt.learning.entity.QuestionEmbeddingCandidate;
import com.jt.learning.mapper.QuestionEmbeddingMapper;
import com.jt.learning.service.ai.AiEmbeddingClient;
import com.jt.learning.vo.QuestionEmbeddingBackfillVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
                service.contentHash("请告诉我车站在哪里。", "问路场景。"),
                "gemini-embedding-001"
        );
        when(mapper.selectRegularQuestionEmbeddingCandidates()).thenReturn(List.of(stale, current));

        QuestionEmbeddingBackfillVO result = service.backfill(100);

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.remainingCount()).isZero();
        verify(mapper, times(1)).upsertQuestionEmbedding(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void isSimilarShouldUseCosineThreshold() {
        QuestionEmbeddingService service = new QuestionEmbeddingService(mock(QuestionEmbeddingMapper.class), mock(AiEmbeddingClient.class));

        assertThat(service.isSimilar(vector(1f), vector(1f))).isTrue();
        assertThat(service.isSimilar(vector(1f), cosineVector(0.8f))).isTrue();
        assertThat(service.isSimilar(vector(1f), cosineVector(0.79f))).isFalse();
        assertThat(service.isSimilar(vector(1f), vectorAt(1, 1f))).isFalse();
    }

    private QuestionEmbeddingCandidate candidate(Long id, String contentHash, String modelName) {
        QuestionEmbeddingCandidate candidate = new QuestionEmbeddingCandidate();
        candidate.setQuestionId(id);
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
