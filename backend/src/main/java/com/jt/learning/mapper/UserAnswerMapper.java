package com.jt.learning.mapper;

import com.jt.learning.dto.UserAnswerDetailRow;
import com.jt.learning.dto.UserAnswerListItemRow;
import com.jt.learning.dto.UserAnswerQueryRequest;
import com.jt.learning.entity.UserAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    int updateReviewEvaluated(
            @Param("id") Long id,
            @Param("aiOverallComment") String aiOverallComment,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    long countUserAnswers(
            @Param("userId") Long userId,
            @Param("request") UserAnswerQueryRequest request
    );

    List<UserAnswerListItemRow> selectUserAnswerList(
            @Param("userId") Long userId,
            @Param("request") UserAnswerQueryRequest request,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    UserAnswerDetailRow selectUserAnswerDetail(
            @Param("userId") Long userId,
            @Param("id") Long id
    );

    UserAnswer selectActiveUserAnswerById(
            @Param("userId") Long userId,
            @Param("id") Long id
    );
}
