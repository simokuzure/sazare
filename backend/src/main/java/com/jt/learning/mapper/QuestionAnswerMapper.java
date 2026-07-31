package com.jt.learning.mapper;

import com.jt.learning.entity.QuestionAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QuestionAnswerMapper {

    int insertQuestionAnswer(QuestionAnswer questionAnswer);

    List<QuestionAnswer> selectActiveAnswersByQuestionId(@Param("questionId") Long questionId);
}
