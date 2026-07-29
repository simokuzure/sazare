package com.jt.learning.mapper;

import com.jt.learning.entity.QuestionAnswer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionAnswerMapper {

    int insertQuestionAnswer(QuestionAnswer questionAnswer);
}
