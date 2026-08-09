package com.jt.learning.service;

import com.jt.learning.dto.LearningStatisticsQueryRequest;
import com.jt.learning.vo.LearningStatisticsVO;

public interface LearningStatisticsService {

    LearningStatisticsVO getLearningStatistics(LearningStatisticsQueryRequest request);
}
