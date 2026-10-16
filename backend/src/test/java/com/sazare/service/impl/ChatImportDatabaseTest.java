package com.sazare.service.impl;

import com.sazare.dto.ChatImportConfirmationRequest;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.service.ChatImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 显式选择已更新约束的测试数据库；每个用例结束自动回滚业务数据。 */
@SpringBootTest(properties = {
        "spring.datasource.url=${CHAT_IMPORT_TEST_DB_URL}",
        "spring.datasource.username=${CHAT_IMPORT_TEST_DB_USER}",
        "spring.datasource.password=${CHAT_IMPORT_TEST_DB_PASSWORD}",
        "ai.provider=mock"
})
@EnabledIfEnvironmentVariable(named = "CHAT_IMPORT_TEST_DB_URL", matches = ".+")
@Transactional
class ChatImportDatabaseTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.sazare.service.ai.client.AiProviderHttpClient aiHttpClient;

    @Autowired private ChatImportService service;
    @Autowired private ErrorTypeMapper errorTypes;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void createsUpdatesAndReactivatesWithoutAnswerRecords() {
        long answersBefore = count("user_answers");
        long errorsBefore = count("user_answer_errors");
        Set<String> excluded = Set.of("OMISSION", "MISTRANSLATION", "ADDITION", "FALSE_FRIEND", "CHINESE_CALQUE");
        Long errorId = errorTypes.selectEnabledLeafOptions().stream().filter(type -> !excluded.contains(type.code()))
                .findFirst().orElseThrow().id();
        var first = service.confirm(new ChatImportConfirmationRequest("ZH_TO_JA", List.of(
                new ChatImportConfirmationRequest.Item("NEW_USER_ERROR_TYPE", errorId, null,
                        "导入事务测试-" + UUID.randomUUID(), "事务回滚，不保存测试数据", "MEDIUM", "我去了学校。", "学校に行きました。"))));
        var card = first.cards().getFirst();
        assertThat(card.action()).isEqualTo("CREATED");
        assertThat(value("select failed_review_count from review_cycles where review_card_id = ?", card.cardId())).isZero();
        var item = new ChatImportConfirmationRequest.Item("EXISTING_USER_ERROR_TYPE", null, card.userErrorTypeId(),
                null, null, "HIGH", "我昨天去了学校。", "昨日学校に行きました。");
        var request = new ChatImportConfirmationRequest("ZH_TO_JA", List.of(item, item));
        assertThat(service.confirm(request).cards().getFirst().action()).isEqualTo("UPDATED");
        assertThat(value("select failed_review_count from review_cycles where review_card_id = ?", card.cardId())).isEqualTo(1);
        assertThat(value("select target_success_count from review_cycles where review_card_id = ?", card.cardId())).isEqualTo(5);
        assertThat(value("select count(*) from review_cycle_questions q join review_cycles c on c.id=q.review_cycle_id where c.review_card_id=?", card.cardId())).isEqualTo(2);
        service.confirm(request);
        assertThat(value("select failed_review_count from review_cycles where review_card_id = ?", card.cardId())).isEqualTo(2);
        jdbc.update("update review_cycles set status='COMPLETED', completed_at=current_timestamp where review_card_id=?", card.cardId());
        jdbc.update("update review_cards set status='MASTERED', due_at=null, mastered_at=current_timestamp where id=?", card.cardId());
        assertThat(service.confirm(request).cards().getFirst().action()).isEqualTo("REACTIVATED");
        assertThat(value("select count(*) from review_cycles where review_card_id=?", card.cardId())).isEqualTo(2);
        assertThat(value("select failed_review_count from review_cycles where review_card_id=? and cycle_no=2", card.cardId())).isEqualTo(1);
        assertThat(value("select count(*) from review_cycle_questions q join review_cycles c on c.id=q.review_cycle_id where c.review_card_id=? and c.cycle_no=2", card.cardId())).isEqualTo(1);
        assertThat(value("select count(*) from review_attempts where review_card_id=? and attempt_source='CHAT_IMPORT' and user_answer_id is null", card.cardId())).isEqualTo(4);
        assertThat(count("user_answers")).isEqualTo(answersBefore);
        assertThat(count("user_answer_errors")).isEqualTo(errorsBefore);
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private int value(String sql, Long id) { return jdbc.queryForObject(sql, Integer.class, id); }
}
