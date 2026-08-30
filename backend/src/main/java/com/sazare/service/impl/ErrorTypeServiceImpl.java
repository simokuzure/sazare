package com.sazare.service.impl;

import com.sazare.dto.ErrorTypeQueryRequest;
import com.sazare.entity.ErrorType;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.service.ErrorTypeService;
import com.sazare.vo.ErrorTypeVO;
import com.sazare.vo.PageVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ErrorTypeServiceImpl implements ErrorTypeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ErrorTypeMapper errorTypeMapper;

    public ErrorTypeServiceImpl(ErrorTypeMapper errorTypeMapper) {
        this.errorTypeMapper = errorTypeMapper;
    }

    @Override
    public PageVO<ErrorTypeVO> listErrorTypes(ErrorTypeQueryRequest request) {
        validateRequest(request);

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

    private void validateRequest(ErrorTypeQueryRequest request) {
        if (request.typeLevel() != null && request.typeLevel() != 1 && request.typeLevel() != 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "typeLevel 只能是 1 或 2");
        }
        if (request.parentId() != null && request.parentId() < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "parentId 必须大于 0");
        }
        if (request.page() < 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "page 必须大于等于 1");
        }
        if (request.size() < 1 || request.size() > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "size 必须在 1 到 100 之间");
        }
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
