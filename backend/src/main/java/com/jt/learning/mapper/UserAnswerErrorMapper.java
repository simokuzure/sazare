package com.jt.learning.mapper;

import com.jt.learning.entity.UserAnswerError;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAnswerErrorMapper {

    int insertUserAnswerError(UserAnswerError userAnswerError);
}
