package com.sazare.service;

import com.sazare.dto.ErrorTypeQueryRequest;
import com.sazare.vo.ErrorTypeVO;
import com.sazare.vo.PageVO;

public interface ErrorTypeService {

    PageVO<ErrorTypeVO> listErrorTypes(ErrorTypeQueryRequest request);
}
