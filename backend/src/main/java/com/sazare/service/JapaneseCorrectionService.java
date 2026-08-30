package com.sazare.service;

import com.sazare.dto.JapaneseCorrectionRequest;
import com.sazare.vo.JapaneseCorrectionReviewVO;

public interface JapaneseCorrectionService {

    JapaneseCorrectionReviewVO correct(JapaneseCorrectionRequest request);
}
