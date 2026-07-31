package com.jt.learning.mapper;

import com.jt.learning.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectEnabledUserByCode(@Param("userCode") String userCode);
}
