package com.jt.learning.mapper;

import com.jt.learning.dto.QuestionQueryRequest;
import com.jt.learning.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QuestionMapper {

    int insertQuestion(Question question);

    Question selectActiveQuestionById(Long id);

    Question selectQuestionById(Long id);

    Question selectQuestionForUpdateById(Long id);

    long countQuestions(@Param("request") QuestionQueryRequest request);

    List<Long> selectQuestionIds(
            @Param("request") QuestionQueryRequest request,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    Long selectRandomQuestionId(@Param("request") QuestionQueryRequest request);

    List<Question> selectQuestionsByIds(@Param("ids") List<Long> ids);

    int updateQuestion(Question question);

    int updateEnabled(
            @Param("id") Long id,
            @Param("enabled") Boolean enabled,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int logicalDelete(
            @Param("id") Long id,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
