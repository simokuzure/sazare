package com.sazare.service;

import com.sazare.dto.LearningStatisticsQueryRequest;
import com.sazare.vo.LearningStatisticsVO;

public interface LearningStatisticsService {

    LearningStatisticsVO getLearningStatistics(LearningStatisticsQueryRequest request);
}
