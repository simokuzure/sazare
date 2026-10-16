package com.sazare.service.ai.validation;

import com.sazare.dto.ChatMessageDTO;
import com.sazare.vo.ChatImportAnalysisVO;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

class ChatImportAiResponseValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatImportAiResponseValidator validator = new ChatImportAiResponseValidator(mapper);
    private final List<ChatMessageDTO> messages = List.of(new ChatMessageDTO(1, "user", "日本に来た前に勉強しました。"),
            new ChatMessageDTO(2, "assistant", "日本に来る前に勉強しました。"), new ChatMessageDTO(3, "user", "ありがとう"));

    @Test
    void acceptsCompleteCoverageAndGroundedEvidence() {
        var result = validate(new ChatImportAnalysisVO(List.of(1, 3), List.of(candidate(1, "来た前に", 4L))));
        assertThat(result.candidates()).hasSize(1);
        assertThat(validate(new ChatImportAnalysisVO(List.of(1, 3), List.of())).candidates()).isEmpty();
    }

    @Test
    void rejectsMissingCoverageAssistantEvidenceAndUnknownTypes() {
        for (var result : List.of(
                new ChatImportAnalysisVO(List.of(1), List.of()),
                new ChatImportAnalysisVO(List.of(1, 1), List.of()),
                new ChatImportAnalysisVO(List.of(1, 3), List.of(candidate(2, "来る前に", 4L))),
                new ChatImportAnalysisVO(List.of(1, 3), List.of(candidate(1, "捏造", 4L))),
                new ChatImportAnalysisVO(List.of(1, 3), List.of(candidate(1, "来た前に", 99L))))) {
            assertThatThrownBy(() -> validate(result)).hasMessageContaining("不完整或不合法");
        }
        assertThatThrownBy(() -> validator.validate("{", messages, Set.of(4L), "ZH_TO_JA"))
                .hasMessageContaining("不完整或不合法");
    }

    private ChatImportAnalysisVO validate(ChatImportAnalysisVO result) {
        return validator.validate(mapper.writeValueAsString(result), messages, Set.of(4L), "ZH_TO_JA");
    }

    private ChatImportAnalysisVO.Candidate candidate(int id, String original, Long type) {
        return new ChatImportAnalysisVO.Candidate(type, "前に的接续", "使用辞书形", "MEDIUM",
                List.of(new ChatImportAnalysisVO.Evidence(id, original)), "来る前に", "来日本之前学习过。", "日本に来る前に勉強しました。");
    }
}
