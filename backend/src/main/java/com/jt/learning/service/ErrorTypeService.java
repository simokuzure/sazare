package com.jt.learning.service;

import com.jt.learning.dto.ErrorTypeQueryRequest;
import com.jt.learning.vo.ErrorTypeVO;
import com.jt.learning.vo.PageVO;

public interface ErrorTypeService {

    PageVO<ErrorTypeVO> listErrorTypes(ErrorTypeQueryRequest request);
}
