package com.jt.learning.mapper;

import com.jt.learning.entity.Question;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionMapper {

    int insertQuestion(Question question);

    Question selectActiveQuestionById(Long id);
}
