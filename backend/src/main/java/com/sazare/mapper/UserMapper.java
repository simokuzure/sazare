package com.sazare.mapper;

import com.sazare.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User selectEnabledUserByCode(@Param("userCode") String userCode);
}
