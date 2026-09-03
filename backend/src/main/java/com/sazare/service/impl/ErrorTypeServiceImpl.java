package com.sazare.service.impl;

import com.sazare.dto.ErrorTypeQueryRequest;
import com.sazare.entity.ErrorType;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.service.ErrorTypeService;
import com.sazare.vo.ErrorTypeVO;
import com.sazare.vo.PageVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ErrorTypeServiceImpl implements ErrorTypeService {

    private final ErrorTypeMapper errorTypeMapper;

    public ErrorTypeServiceImpl(ErrorTypeMapper errorTypeMapper) {
        this.errorTypeMapper = errorTypeMapper;
    }

    @Override
    public PageVO<ErrorTypeVO> listErrorTypes(ErrorTypeQueryRequest request) {
        long total = errorTypeMapper.countErrorTypes(request);
        if (total == 0) {
            return new PageVO<>(List.of(), request.page(), request.size(), 0);
        }

        long offset = (long) (request.page() - 1) * request.size();
        List<ErrorTypeVO> items = errorTypeMapper.selectErrorTypeList(request, request.size(), offset)
                .stream()
                .map(this::toVO)
                .toList();
        return new PageVO<>(items, request.page(), request.size(), total);
    }

    private ErrorTypeVO toVO(ErrorType errorType) {
        return new ErrorTypeVO(
                errorType.getId(),
                errorType.getParentId(),
                errorType.getTypeLevel(),
                errorType.getCode(),
                errorType.getName(),
                errorType.getDescription(),
                errorType.getNameEn(),
                errorType.getDescriptionEn(),
                errorType.getSortOrder(),
                errorType.getEnabled(),
                errorType.getCreatedAt(),
                errorType.getUpdatedAt()
        );
    }
}
