package com.sazare.mapper;

import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.ErrorTypeQueryRequest;
import com.sazare.entity.ErrorType;
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
