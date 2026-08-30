package com.sazare.mapper;

import com.sazare.entity.UserAnswerError;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAnswerErrorMapper {

    int insertUserAnswerError(UserAnswerError userAnswerError);
}
