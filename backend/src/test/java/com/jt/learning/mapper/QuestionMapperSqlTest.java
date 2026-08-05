package com.jt.learning.mapper;

import com.jt.learning.dto.QuestionQueryRequest;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionMapperSqlTest {

    @Test
    void randomQuestionSqlShouldExcludeReviewDerivedQuestions() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/QuestionMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/QuestionMapper.xml", configuration.getSqlFragments())
                    .parse();
        }
        QuestionQueryRequest request = new QuestionQueryRequest(
                null, null, null, null, null, null, null, null, true, 1, 1);
        BoundSql boundSql = configuration
                .getMappedStatement("com.jt.learning.mapper.QuestionMapper.selectRandomQuestionId")
                .getBoundSql(Map.of("request", request));

        assertThat(boundSql.getSql()).contains("q.source_type <> 'REVIEW_DERIVED'");
    }
}
