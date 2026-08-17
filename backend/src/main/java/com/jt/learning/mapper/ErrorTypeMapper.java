package com.jt.learning.mapper;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.ErrorTypeQueryRequest;
import com.jt.learning.entity.ErrorType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ErrorTypeMapper {

    List<AiErrorTypeOptionDTO> selectEnabledLeafOptions();

    ErrorType selectEnabledLeafById(Long id);

    ErrorType selectEnabledLeafByCode(String code);

    long countErrorTypes(@Param("request") ErrorTypeQueryRequest request);

    List<ErrorType> selectErrorTypeList(
            @Param("request") ErrorTypeQueryRequest request,
            @Param("limit") int limit,
            @Param("offset") long offset
    );
}
