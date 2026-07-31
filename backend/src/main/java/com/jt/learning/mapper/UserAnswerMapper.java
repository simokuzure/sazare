package com.jt.learning.mapper;

import com.jt.learning.entity.UserAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface UserAnswerMapper {

    int insertUserAnswer(UserAnswer userAnswer);

    int updateReviewed(
            @Param("id") Long id,
            @Param("grammarVocabularyScore") Integer grammarVocabularyScore,
            @Param("naturalFluencyScore") Integer naturalFluencyScore,
            @Param("scenarioAdaptationScore") Integer scenarioAdaptationScore,
            @Param("informationCompletenessScore") Integer informationCompletenessScore,
            @Param("totalScore") BigDecimal totalScore,
            @Param("aiOverallComment") String aiOverallComment,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int updateFailed(
            @Param("id") Long id,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
