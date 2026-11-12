package com.sazare.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=${CHAT_IMPORT_TEST_DB_URL}",
        "spring.datasource.username=${CHAT_IMPORT_TEST_DB_USER}",
        "spring.datasource.password=${CHAT_IMPORT_TEST_DB_PASSWORD}",
        "ai.provider=mock"
})
@EnabledIfEnvironmentVariable(named = "CHAT_IMPORT_TEST_DB_URL", matches = ".+")
@Transactional
class ReviewCardListDatabaseTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.sazare.service.ai.client.AiProviderHttpClient aiHttpClient;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReviewCardMapper mapper;

    private final LocalDateTime now = LocalDateTime.of(2030, 1, 1, 7, 0);

    @Test
    void dueAndWaitingPartitionAllActiveCardsAtExactBoundary() {
        Long before = card("ACTIVE", now.minusSeconds(1), 0, now, "ZH_TO_JA", false);
        Long boundary = card("ACTIVE", now, 0, now, "ZH_TO_JA", false);
        Long after = card("ACTIVE", now.plusSeconds(1), 0, now, "ZH_TO_JA", false);
        card("ACTIVE", now.minusDays(1), 0, now, "EN_TO_JA", false);
        card("ACTIVE", now.minusDays(1), 0, now, "ZH_TO_JA", true);

        assertThat(ids("ACTIVE", true, false, 100, 0)).containsExactly(before, boundary);
        assertThat(ids("ACTIVE", false, true, 100, 0)).containsExactly(after);
        assertThat(ids("ACTIVE", false, false, 100, 0)).containsExactly(before, boundary, after);
        assertThat(mapper.countCards(userId(), "ACTIVE", "ZH_TO_JA", true, false, now)).isEqualTo(2);
        assertThat(mapper.countCards(userId(), "ACTIVE", "ZH_TO_JA", false, true, now)).isEqualTo(1);
        assertThat(mapper.countCards(userId(), "ACTIVE", "ZH_TO_JA", false, false, now)).isEqualTo(3);
    }

    @Test
    void earliestDueWinsThenMoreFailuresThenStableIdAcrossPages() {
        Long early = card("ACTIVE", now.minusDays(2), 0, now, "ZH_TO_JA", false);
        Long fewerFailures = card("ACTIVE", now.minusDays(1), 1, now.plusDays(1), "ZH_TO_JA", false);
        Long moreFailures = card("ACTIVE", now.minusDays(1), 3, now.minusDays(1), "ZH_TO_JA", false);
        Long samePriority = card("ACTIVE", now.minusDays(1), 3, now, "ZH_TO_JA", false);

        assertThat(ids("ACTIVE", true, false, 2, 0)).containsExactly(early, moreFailures);
        assertThat(ids("ACTIVE", true, false, 2, 2)).containsExactly(samePriority, fewerFailures);
    }

    @Test
    void waitingUsesNextDueAndMasteredUsesActualMasteryTime() {
        Long later = card("ACTIVE", now.plusDays(2), 0, now.plusDays(1), "ZH_TO_JA", false);
        Long nearer = card("ACTIVE", now.plusDays(1), 0, now, "ZH_TO_JA", false);
        Long sameDue = card("ACTIVE", now.plusDays(1), 0, now.plusDays(2), "ZH_TO_JA", false);
        assertThat(ids("ACTIVE", false, true, 100, 0)).containsExactly(nearer, sameDue, later);

        Long old = card("MASTERED", now.minusDays(2), 0, now.plusDays(10), "ZH_TO_JA", false);
        Long recent = card("MASTERED", now.minusDays(1), 0, now, "ZH_TO_JA", false);
        Long sameMastery = card("MASTERED", now.minusDays(1), 0, now.minusDays(1), "ZH_TO_JA", false);
        assertThat(ids("MASTERED", false, false, 100, 0)).containsExactly(sameMastery, recent, old);
    }

    private List<Long> ids(String status, boolean dueOnly, boolean waitingOnly, int limit, int offset) {
        return mapper.selectCardList(userId(), status, "ZH_TO_JA", dueOnly, waitingOnly, now, limit, offset)
                .stream().map(com.sazare.dto.ReviewCardListRow::getId).toList();
    }

    private Long userId() {
        return jdbc.queryForObject("select id from users where user_code='LOCAL_DEFAULT'", Long.class);
    }

    private Long card(String status, LocalDateTime scheduledAt, int failures,
                      LocalDateTime updatedAt, String mode, boolean deleted) {
        Long errorId = jdbc.queryForObject("select id from error_types where type_level=2 limit 1", Long.class);
        Long typeId = jdbc.queryForObject("""
                insert into user_error_types(user_id,error_type_id,learning_mode,name)
                values(?,?,?,?) returning id
                """, Long.class, userId(), errorId, mode, "列表测试-" + UUID.randomUUID());
        boolean mastered = "MASTERED".equals(status);
        Long cardId = jdbc.queryForObject("""
                insert into review_cards(user_id,user_error_type_id,status,due_at,mastered_at,updated_at,deleted)
                values(?,?,?,?,?,?,?) returning id
                """, Long.class, userId(), typeId, status, mastered ? null : scheduledAt,
                mastered ? scheduledAt : null, updatedAt, deleted);
        jdbc.update("""
                insert into review_cycles(review_card_id,cycle_no,status,failed_review_count,completed_at)
                values(?,1,?,?,?)
                """, cardId, mastered ? "COMPLETED" : "IN_PROGRESS", failures, mastered ? scheduledAt : null);
        return cardId;
    }
}
