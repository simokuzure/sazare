package com.sazare.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewCardMapperSqlTest {

    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream("mapper/ReviewCardMapper.xml")) {
            new XMLMapperBuilder(input, configuration, "mapper/ReviewCardMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void waitingCountAndPageShareStrictlyFutureFilter() {
        assertThat(sql("countCards", "ACTIVE", false, true)).contains("card.due_at > ?").doesNotContain("card.due_at <= ?");
        assertThat(sql("selectCardList", "ACTIVE", false, true)).contains("card.due_at > ?")
                .contains("order by card.due_at asc nulls last, card.id asc");
    }

    @Test
    void dueCardsUseFailureCountOnlyAfterDueTimeAndKeepStableTies() {
        assertThat(sql("countCards", "ACTIVE", true, false)).contains("card.due_at <= ?");
        assertThat(sql("selectCardList", "ACTIVE", true, false))
                .contains("order by card.due_at asc, coalesce(cycle.failed_review_count, 0) desc, card.id asc");
    }

    @Test
    void allActiveCardsRemainAvailableToChatImport() {
        assertThat(sql("selectCardList", "ACTIVE", false, false))
                .doesNotContain("card.due_at > ?", "card.due_at <= ?");
        assertThat(sql("countCards", "ACTIVE", false, false))
                .doesNotContain("card.due_at > ?", "card.due_at <= ?");
    }

    @Test
    void masteredCardsUseMasteryTimeInsteadOfIncidentalUpdates() {
        assertThat(sql("selectCardList", "MASTERED", false, false))
                .contains("order by card.mastered_at desc nulls last, card.id desc")
                .doesNotContain("card.updated_at desc");
    }

    private String sql(String statement, String status, boolean dueOnly, boolean waitingOnly) {
        return configuration.getMappedStatement("com.sazare.mapper.ReviewCardMapper." + statement)
                .getBoundSql(Map.of("userId", 1L, "status", status, "learningMode", "ZH_TO_JA",
                        "dueOnly", dueOnly, "waitingOnly", waitingOnly, "now", LocalDateTime.now(),
                        "limit", 20, "offset", 0)).getSql();
    }
}
