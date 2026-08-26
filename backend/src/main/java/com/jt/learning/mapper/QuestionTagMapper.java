package com.jt.learning.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QuestionTagMapper {

    int insertQuestionTag(
            @Param("questionId") Long questionId,
            @Param("tagId") Long tagId
    );

    int countByQuestionId(@Param("questionId") Long questionId);

    int deleteQuestionTagsByQuestionId(@Param("questionId") Long questionId);
}
