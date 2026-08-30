package com.sazare.mapper;

import com.sazare.dto.QuestionEmbeddingMatch;
import com.sazare.entity.QuestionEmbeddingCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuestionEmbeddingMapper {

    List<QuestionEmbeddingMatch> selectSimilarQuestionEmbeddings(
            @Param("embedding") String embedding,
            @Param("threshold") double threshold,
            @Param("questionType") String questionType
    );

    List<QuestionEmbeddingCandidate> selectRegularQuestionEmbeddingCandidates();

    int upsertQuestionEmbedding(
            @Param("questionId") Long questionId,
            @Param("embedding") String embedding,
            @Param("contentHash") String contentHash,
            @Param("modelName") String modelName,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
