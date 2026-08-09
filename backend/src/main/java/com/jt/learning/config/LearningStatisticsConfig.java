package com.jt.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class LearningStatisticsConfig {

    public static final ZoneId STATISTICS_ZONE_ID = ZoneId.of("Asia/Tokyo");

    @Bean
    public Clock learningStatisticsClock() {
        return Clock.system(STATISTICS_ZONE_ID);
    }
}
