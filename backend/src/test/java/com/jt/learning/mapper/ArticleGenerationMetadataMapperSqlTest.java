package com.jt.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleGenerationMetadataMapperSqlTest {

    @Test
    void insertSqlShouldPersistSeedAndJsonBlueprint() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/ArticleGenerationMetadataMapper.xml")) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    "mapper/ArticleGenerationMetadataMapper.xml",
                    configuration.getSqlFragments()
            ).parse();
        }

        BoundSql boundSql = configuration
                .getMappedStatement("com.jt.learning.mapper.ArticleGenerationMetadataMapper.insertArticleGenerationMetadata")
                .getBoundSql(Map.of(
                        "questionId", 1L,
                        "seed", UUID.randomUUID(),
                        "blueprint", "{\"coreConcept\":\"概念\",\"roles\":{}}",
                        "createdAt", LocalDateTime.now()
                ));

        assertThat(boundSql.getSql())
                .contains("insert into article_generation_metadata")
                .contains("cast(? as jsonb)");
    }
}
