package com.jt.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LearningStatisticsMapperSqlTest {

    @Test
    void weaknessSqlShouldReadOnlyPersistedConfirmedErrorsAndExcludeDeletedAnswers() throws Exception {
        BoundSql boundSql = getBoundSql("selectTopWeaknesses");

        assertThat(boundSql.getSql())
                .contains("from user_answer_errors answer_error")
                .contains("user_answer.deleted = false")
                .contains("order by confirmed_count desc")
                .doesNotContain("errorAnalysis");
    }

    @Test
    void periodReviewSqlShouldExcludePracticeErrorEvents() throws Exception {
        BoundSql boundSql = getBoundSql("selectPeriodReviewSummary");

        assertThat(boundSql.getSql())
                .contains("review_attempt.attempt_source = 'REVIEW'")
                .contains("review_attempt.created_at >= ?")
                .contains("review_attempt.created_at < ?");
    }

    @Test
    void currentReviewSqlShouldQualifyReviewCardStatusAndDueDate() throws Exception {
        BoundSql boundSql = getBoundSql("selectCurrentReviewSummary");

        assertThat(boundSql.getSql())
                .contains("review_card.status = 'ACTIVE'")
                .contains("review_card.status = 'MASTERED'")
                .contains("review_card.due_at <= ?");
    }

    private BoundSql getBoundSql(String statementId) throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/LearningStatisticsMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/LearningStatisticsMapper.xml", configuration.getSqlFragments())
                    .parse();
        }
        return configuration
                .getMappedStatement("com.jt.learning.mapper.LearningStatisticsMapper." + statementId)
                .getBoundSql(Map.of(
                        "userId", 1L,
                        "startAt", LocalDateTime.of(2026, 8, 1, 0, 0),
                        "endAt", LocalDateTime.of(2026, 8, 2, 0, 0),
                        "now", LocalDateTime.of(2026, 8, 1, 12, 0)
                ));
    }
}
