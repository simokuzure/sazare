package com.sazare.mapper;

import com.sazare.dto.QuestionQueryRequest;
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
    void listQuestionSqlShouldScopeAllTypesToLearningMode() throws Exception {
        Configuration configuration = loadConfiguration();
        QuestionQueryRequest directionRequest = new QuestionQueryRequest(
                "EN_TO_JA", null, null, null, null, null, null, null, null, true, 1, 20);
        QuestionQueryRequest shortQuestionRequest = new QuestionQueryRequest(
                null, "TRANSLATION_ZH_TO_JA", null, null, null, null, null, null, null, true, 1, 20);

        BoundSql directionSql = configuration
                .getMappedStatement("com.sazare.mapper.QuestionMapper.countQuestions")
                .getBoundSql(Map.of("request", directionRequest));
        BoundSql shortQuestionSql = configuration
                .getMappedStatement("com.sazare.mapper.QuestionMapper.countQuestions")
                .getBoundSql(Map.of("request", shortQuestionRequest));

        assertThat(directionRequest.getQuestionTypes())
                .containsExactly("TRANSLATION_EN_TO_JA", "TRANSLATION_EN_TO_JA_ARTICLE");
        assertThat(directionSql.getSql()).contains("q.question_type in");
        assertThat(directionSql.getParameterMappings())
                .filteredOn(mapping -> mapping.getProperty().startsWith("__frch_questionType"))
                .hasSize(2);
        assertThat(shortQuestionRequest.getQuestionTypes()).containsExactly("TRANSLATION_ZH_TO_JA");
        assertThat(shortQuestionSql.getSql()).contains("q.question_type in");
        assertThat(shortQuestionSql.getParameterMappings())
                .filteredOn(mapping -> mapping.getProperty().startsWith("__frch_questionType"))
                .hasSize(1);
    }

    @Test
    void randomQuestionSqlShouldExcludeReviewDerivedQuestionsAndUseRequestedLimit() throws Exception {
        Configuration configuration = loadConfiguration();
        QuestionQueryRequest request = new QuestionQueryRequest(
                null, "TRANSLATION_ZH_TO_JA", null, null, null, null, null, null, null, true, 1, 1);
        BoundSql boundSql = configuration
                .getMappedStatement("com.sazare.mapper.QuestionMapper.selectRandomQuestionIds")
                .getBoundSql(Map.of("request", request, "limit", 3));

        assertThat(boundSql.getSql())
                .contains("q.source_type <> 'REVIEW_DERIVED'")
                .contains("limit ?");
        assertThat(boundSql.getParameterMappings()).extracting("property").contains("limit");
    }

    private static Configuration loadConfiguration() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/QuestionMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/QuestionMapper.xml", configuration.getSqlFragments())
                    .parse();
        }
        return configuration;
    }
}
