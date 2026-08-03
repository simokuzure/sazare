package com.jt.learning.mapper;

import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.entity.ErrorType;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErrorTypeMapper {

    List<AiErrorTypeOptionDTO> selectEnabledLeafOptions();

    ErrorType selectEnabledLeafById(Long id);
}
