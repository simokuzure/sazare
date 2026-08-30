package com.sazare.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionEmbeddingMapperSqlTest {

    @Test
    void similarQuestionSqlShouldOnlyCompareRegularNonDeletedQuestions() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/QuestionEmbeddingMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/QuestionEmbeddingMapper.xml", configuration.getSqlFragments())
                    .parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.sazare.mapper.QuestionEmbeddingMapper.selectSimilarQuestionEmbeddings")
                .getBoundSql(Map.of("embedding", "[0.1]", "threshold", 0.8d));

        assertThat(boundSql.getSql())
                .contains("q.source_text")
                .contains("q.deleted = false")
                .contains("q.source_type in ('AI', 'MANUAL')")
                .contains("qe.embedding <=> cast(? as vector)");
    }

    @Test
    void embeddingCandidateSqlShouldReturnQuestionTypeForContentSelection() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/QuestionEmbeddingMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/QuestionEmbeddingMapper.xml", configuration.getSqlFragments())
                    .parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.sazare.mapper.QuestionEmbeddingMapper.selectRegularQuestionEmbeddingCandidates")
                .getBoundSql(Map.of());

        assertThat(boundSql.getSql()).contains("q.question_type");
    }
}
