package com.sazare.service.ai.validation;

import com.sazare.dto.ChatMessageDTO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.util.ChatReviewTextValidator;
import com.sazare.vo.ChatImportAnalysisVO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChatImportAiResponseValidator {
    private final ObjectMapper mapper;

    public ChatImportAiResponseValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public ChatImportAnalysisVO validate(String json, List<ChatMessageDTO> messages, Set<Long> typeIds, String mode) {
        try {
            ChatImportAnalysisVO result = mapper.readValue(json, ChatImportAnalysisVO.class);
            Map<Integer, ChatMessageDTO> users = messages.stream().filter(m -> "user".equals(m.role()))
                    .collect(Collectors.toMap(ChatMessageDTO::id, Function.identity()));
            if (result == null || result.checkedUserMessageIds() == null || result.candidates() == null
                    || result.candidates().size() > 200
                    || result.checkedUserMessageIds().size() != users.size()
                    || !new HashSet<>(result.checkedUserMessageIds()).equals(users.keySet())) throw invalid();
            Set<String> focuses = new HashSet<>();
            for (var candidate : result.candidates()) {
                if (candidate == null || !typeIds.contains(candidate.errorTypeId())
                        || !text(candidate.name(), 128) || !text(candidate.description(), 255)
                        || !text(candidate.reviewSourceText(), 1000) || !text(candidate.referenceAnswer(), 2000)
                        || !text(candidate.correctedExpression(), 2000)
                        || candidate.severity() == null || !Set.of("LOW", "MEDIUM", "HIGH").contains(candidate.severity())
                        || candidate.evidence() == null || candidate.evidence().isEmpty()
                        || !focuses.add(candidate.errorTypeId() + ":" + candidate.name().trim())) throw invalid();
                for (var evidence : candidate.evidence()) {
                    if (evidence == null || !users.containsKey(evidence.messageId())
                            || !text(evidence.originalText(), 30_000)
                            || !users.get(evidence.messageId()).content().contains(evidence.originalText())) throw invalid();
                }
                ChatReviewTextValidator.validate(candidate.reviewSourceText(), candidate.referenceAnswer(), mode);
            }
            return result;
        } catch (RuntimeException exception) {
            // 不携带解析器异常，避免错误日志包含聊天内容。
            throw invalid();
        }
    }

    private boolean text(String text, int max) { return text != null && !text.isBlank() && text.length() <= max; }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析结果不完整或不合法，请重新分析");
    }
}
