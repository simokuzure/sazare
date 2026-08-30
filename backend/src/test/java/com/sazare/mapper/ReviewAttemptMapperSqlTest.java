package com.sazare.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewAttemptMapperSqlTest {

    @Test
    void reviewHistorySqlShouldReadOnlyCurrentUsersFormalReviewAttemptsNewestFirst() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/ReviewAttemptMapper.xml")) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    "mapper/ReviewAttemptMapper.xml",
                    configuration.getSqlFragments()
            ).parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.sazare.mapper.ReviewAttemptMapper.selectReviewHistory")
                .getBoundSql(Map.of("reviewCardId", 1L, "userId", 2L));

        assertThat(boundSql.getSql())
                .contains("attempt.review_card_id = ?")
                .contains("attempt.user_id = ?")
                .contains("attempt.attempt_source = 'REVIEW'")
                .contains("user_answer.deleted = false")
                .contains("question.deleted = false")
                .contains("from question_answers answer")
                .contains("answer.deleted = false")
                .contains("case when answer.primary_answer = true then 0 else 1 end")
                .contains("answer.sort_order asc")
                .contains("limit 1")
                .contains("order by attempt.created_at desc, attempt.id desc");
    }
}
