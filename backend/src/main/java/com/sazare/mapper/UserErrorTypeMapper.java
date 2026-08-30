package com.sazare.mapper;

import com.sazare.dto.UserErrorTypeListItemRow;
import com.sazare.entity.UserErrorType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserErrorTypeMapper {

    UserErrorType selectActiveByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    UserErrorType selectActiveByUserIdAndErrorTypeIdAndName(
            @Param("userId") Long userId,
            @Param("errorTypeId") Long errorTypeId,
            @Param("learningMode") String learningMode,
            @Param("name") String name
    );

    int insertUserErrorType(UserErrorType userErrorType);

    int archiveByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId,
                             @Param("updatedAt") LocalDateTime updatedAt);

    long countUserErrorTypes(@Param("userId") Long userId, @Param("status") String status,
                             @Param("learningMode") String learningMode);

    List<UserErrorTypeListItemRow> selectUserErrorTypeList(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("learningMode") String learningMode,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
