package com.sazare.service.ai.validation;

import com.sazare.dto.ChatMessageDTO;
import com.sazare.dto.AiChatImportAnalysisDTO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.util.ChatReviewTextValidator;
import com.sazare.vo.ChatImportAnalysisVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ChatImportAiResponseValidator {
    private static final Logger log = LoggerFactory.getLogger(ChatImportAiResponseValidator.class);
    private final ObjectMapper mapper;

    public ChatImportAiResponseValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public ChatImportAnalysisVO validate(String json, List<ChatMessageDTO> messages, Set<Long> typeIds, String mode) {
        try {
            AiChatImportAnalysisDTO result = mapper.readValue(json, AiChatImportAnalysisDTO.class);
            List<Integer> userIds = messages.stream().filter(m -> "user".equals(m.role())).map(ChatMessageDTO::id).toList();
            Map<Integer, ChatMessageDTO> users = messages.stream().filter(m -> "user".equals(m.role()))
                    .collect(Collectors.toMap(ChatMessageDTO::id, Function.identity()));
            if (result == null) throw invalid("AI 未返回分析对象");
            if (result.messageChecks() == null) throw invalid("缺少逐条消息检查记录（messageChecks）");
            if (result.candidates() == null) throw invalid("缺少候选列表（candidates）");
            if (result.candidates().size() > 200) throw invalid("候选数量超过 200 项");
            if (result.messageChecks().size() != users.size()) {
                throw invalid("消息检查记录数量不符，应为 " + users.size() + " 条，实际为 " + result.messageChecks().size() + " 条");
            }
            Set<FindingKey> checkedFindings = validateMessageChecks(result, userIds, users);
            Set<FindingKey> candidateFindings = validateCandidates(result.candidates(), users, typeIds, mode);
            for (var finding : checkedFindings) {
                if (!candidateFindings.contains(finding)) {
                    throw invalid("用户消息 #" + finding.messageId() + " 的检查发现与第 "
                            + (finding.candidateIndex() + 1) + " 个候选的引用不一致");
                }
            }
            for (var evidence : candidateFindings) {
                if (!checkedFindings.contains(evidence)) {
                    throw invalid("第 " + (evidence.candidateIndex() + 1) + " 个候选引用的用户消息 #"
                            + evidence.messageId() + " 引用未列入检查发现");
                }
            }
            return new ChatImportAnalysisVO(userIds, result.candidates());
        } catch (BusinessException exception) {
            throw exception;
        } catch (JacksonException exception) {
            // 不携带解析器异常，避免错误日志包含聊天内容。
            throw invalid("AI 返回的 JSON 格式或字段类型不符合约定");
        } catch (RuntimeException exception) {
            log.error("聊天分析结果校验发生内部异常: exceptionType={}", exception.getClass().getName());
            throw invalid("校验分析结果时发生内部异常");
        }
    }

    private Set<FindingKey> validateCandidates(List<ChatImportAnalysisVO.Candidate> candidates,
            Map<Integer, ChatMessageDTO> users, Set<Long> typeIds, String mode) {
        Set<FindingKey> candidateFindings = new LinkedHashSet<>();
        Set<String> focuses = new HashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            var candidate = candidates.get(index);
            String location = "第 " + (index + 1) + " 个候选";
            if (candidate == null) throw invalid(location + "不能为空");
            if (candidate.errorTypeId() == null || !typeIds.contains(candidate.errorTypeId())) {
                throw invalid(location + "的错误分类不在可选范围内");
            }
            requireText(candidate.name(), 128, location + "的重点名称");
            requireText(candidate.description(), 255, location + "的解释");
            requireText(candidate.reviewSourceText(), 1000, location + "的复习题面");
            requireText(candidate.referenceAnswer(), 2000, location + "的参考答案");
            requireText(candidate.correctedExpression(), 2000, location + "的修正表达");
            if (candidate.severity() == null || !Set.of("LOW", "MEDIUM", "HIGH").contains(candidate.severity())) {
                throw invalid(location + "的严重程度必须为 LOW、MEDIUM 或 HIGH");
            }
            if (candidate.evidence() == null || candidate.evidence().isEmpty()) throw invalid(location + "缺少原文引用");
            if (!focuses.add(candidate.errorTypeId() + ":" + candidate.name().trim())) throw invalid(location + "的分类和重点名称与前面的候选重复");
            for (int evidenceIndex = 0; evidenceIndex < candidate.evidence().size(); evidenceIndex++) {
                var evidence = candidate.evidence().get(evidenceIndex);
                String evidenceLocation = location + "的第 " + (evidenceIndex + 1) + " 条引用";
                if (evidence == null || !users.containsKey(evidence.messageId())) throw invalid(evidenceLocation + "未引用有效用户消息");
                requireText(evidence.originalText(), 30_000, evidenceLocation + "的原文片段");
                if (!users.get(evidence.messageId()).content().contains(evidence.originalText())) {
                    throw invalid(evidenceLocation + "与用户消息 #" + evidence.messageId() + " 的原文不一致");
                }
                if (!candidateFindings.add(new FindingKey(evidence.messageId(), evidence.originalText(), index))) {
                    throw invalid(evidenceLocation + "与该候选前面的引用重复");
                }
            }
            try {
                ChatReviewTextValidator.validate(candidate.reviewSourceText(), candidate.referenceAnswer(), mode);
            } catch (BusinessException exception) {
                throw invalid(location + "：" + exception.getMessage());
            }
        }
        return candidateFindings;
    }

    private Set<FindingKey> validateMessageChecks(AiChatImportAnalysisDTO result, List<Integer> userIds,
                                                 Map<Integer, ChatMessageDTO> users) {
        Set<FindingKey> findings = new LinkedHashSet<>();
        for (int index = 0; index < userIds.size(); index++) {
            var check = result.messageChecks().get(index);
            String location = "用户消息 #" + userIds.get(index);
            if (check == null) throw invalid(location + "的检查记录为空");
            if (!userIds.get(index).equals(check.messageId())) throw invalid("第 " + (index + 1) + " 条检查记录的消息编号或顺序不正确，应对应" + location);
            if (check.findings() == null) throw invalid(location + "缺少发现清单（findings）");
            for (int findingIndex = 0; findingIndex < check.findings().size(); findingIndex++) {
                var finding = check.findings().get(findingIndex);
                String findingLocation = location + "的第 " + (findingIndex + 1) + " 条发现";
                if (finding == null) throw invalid(findingLocation + "不能为空");
                requireText(finding.originalText(), 30_000, findingLocation + "的原文片段");
                if (!users.get(check.messageId()).content().contains(finding.originalText())) throw invalid(findingLocation + "引用的片段与原文不一致");
                if (finding.candidateIndex() == null || finding.candidateIndex() < 0 || finding.candidateIndex() >= result.candidates().size()) {
                    throw invalid(findingLocation + "的 candidateIndex 无效，应为从 0 开始的有效候选下标");
                }
                if (!findings.add(new FindingKey(check.messageId(), finding.originalText(), finding.candidateIndex()))) throw invalid(findingLocation + "与前面的发现重复");
            }
        }
        return findings;
    }

    private record FindingKey(int messageId, String originalText, int candidateIndex) { }

    private void requireText(String text, int max, String field) {
        if (text == null || text.isBlank()) throw invalid(field + "不能为空");
        if (text.length() > max) throw invalid(field + "不能超过 " + max + " 个字符");
    }

    private BusinessException invalid(String reason) {
        log.warn("聊天分析结果校验失败: reason={}", reason);
        return new BusinessException(ErrorCode.BUSINESS_ERROR, "聊天分析结果不完整或不合法：" + reason + "，请重新分析");
    }
}
