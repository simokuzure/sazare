package com.jt.learning.service;

import com.jt.learning.dto.JapaneseCorrectionRequest;
import com.jt.learning.vo.JapaneseCorrectionReviewVO;

public interface JapaneseCorrectionService {

    JapaneseCorrectionReviewVO correct(JapaneseCorrectionRequest request);
}
