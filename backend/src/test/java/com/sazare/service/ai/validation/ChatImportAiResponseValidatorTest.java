package com.sazare.service.ai.validation;

import com.sazare.dto.ChatMessageDTO;
import com.sazare.dto.AiChatImportAnalysisDTO;
import com.sazare.dto.AiChatImportAnalysisDTO.Finding;
import com.sazare.dto.AiChatImportAnalysisDTO.MessageCheck;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.client.MockAiChatImportClient;
import com.sazare.service.ai.prompt.AiChatImportPromptBuilder;
import com.sazare.vo.ChatImportAnalysisVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
    void acceptsCompleteChecksAndKeepsPublicResponseUnchanged() {
        var result = validate(new AiChatImportAnalysisDTO(List.of(
                check(1, new Finding("来た前に", 0)), check(3)), List.of(candidate(1, "来た前に", 4L))));
        assertThat(result.checkedUserMessageIds()).containsExactly(1, 3);
        assertThat(result.candidates()).hasSize(1);
        assertThat(mapper.writeValueAsString(result)).doesNotContain("messageChecks", "findings", "candidateIndex");
        assertThat(validate(new AiChatImportAnalysisDTO(List.of(check(1), check(3)), List.of())).candidates()).isEmpty();
    }

    @Test
    void rejectsMissingDuplicateOrOutOfOrderChecks() {
        for (var checks : List.of(List.of(check(1)), List.of(check(1), check(1)), List.of(check(3), check(1)))) {
            assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of())))
                    .hasMessageContaining("不完整或不合法");
        }
        assertThatThrownBy(() -> validator.validate("{", messages, Set.of(4L), "ZH_TO_JA"))
                .hasMessageContaining("不完整或不合法");
        assertThatThrownBy(() -> validator.validate("{\"checkedUserMessageIds\":[1,3],\"candidates\":[]}",
                messages, Set.of(4L), "ZH_TO_JA")).hasMessageContaining("不完整或不合法");
    }

    @Test
    void rejectsFindingsThatAreDroppedDuringMergingOrHaveNoMatchingEvidence() {
        var candidates = List.of(candidate(1, "来た前に", 4L));
        for (var check : List.of(
                check(1),
                check(1, new Finding("来た前に", 1)),
                check(1, new Finding("来た前に", -1)),
                check(1, new Finding("来た前に", null)),
                check(1, new Finding("日本に来た前に", 0)),
                check(1, new Finding("捏造", 0)))) {
            assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(check, check(3)), candidates)))
                    .hasMessageContaining("不完整或不合法");
        }
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(
                check(1, new Finding("来た前に", 0)), check(3)), List.of())))
                .hasMessageContaining("不完整或不合法");
    }

    @Test
    void rejectsAssistantEvidenceUngroundedEvidenceAndUnknownTypes() {
        for (var candidate : List.of(candidate(2, "来る前に", 4L), candidate(1, "捏造", 4L), candidate(1, "来た前に", 99L))) {
            assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(
                    check(1, new Finding("来た前に", 0)), check(3)), List.of(candidate))))
                    .hasMessageContaining("不完整或不合法");
        }
    }

    @Test
    void mapsIndependentFindingsInOneMessageToSeparateCandidates() {
        var input = List.of(new ChatMessageDTO(1, "user", "昨日、図書館に日本語を勉強するました。"));
        var location = new ChatImportAnalysisVO.Candidate(4L, "动作发生场所", "使用で", "MEDIUM",
                List.of(new ChatImportAnalysisVO.Evidence(1, "図書館に")), "図書館で", "我在图书馆学习日语。", "図書館で日本語を勉強します。");
        var conjugation = new ChatImportAnalysisVO.Candidate(4L, "する的过去敬体", "使用しました", "MEDIUM",
                List.of(new ChatImportAnalysisVO.Evidence(1, "勉強するました")), "勉強しました", "我昨天学习了日语。", "昨日、日本語を勉強しました。");
        var response = new AiChatImportAnalysisDTO(List.of(check(1,
                new Finding("図書館に", 0), new Finding("勉強するました", 1))), List.of(location, conjugation));
        assertThat(validator.validate(mapper.writeValueAsString(response), input, Set.of(4L), "ZH_TO_JA").candidates())
                .containsExactly(location, conjugation);
    }

    @Test
    void retainsEvidenceFromEveryMessageWhenFindingsShareOneCandidate() {
        var input = List.of(messages.getFirst(), new ChatMessageDTO(3, "user", "ご飯を食べた前に手を洗います。"));
        var candidate = new ChatImportAnalysisVO.Candidate(4L, "前に的接续", "使用辞书形", "MEDIUM",
                List.of(new ChatImportAnalysisVO.Evidence(1, "来た前に"), new ChatImportAnalysisVO.Evidence(3, "食べた前に")),
                "来る前に", "来日本之前学习过。", "日本に来る前に勉強しました。");
        var response = new AiChatImportAnalysisDTO(List.of(
                check(1, new Finding("来た前に", 0)), check(3, new Finding("食べた前に", 0))), List.of(candidate));
        assertThat(validator.validate(mapper.writeValueAsString(response), input, Set.of(4L), "ZH_TO_JA")
                .candidates().getFirst().evidence()).hasSize(2);
        var omitted = new AiChatImportAnalysisDTO(List.of(check(1, new Finding("来た前に", 0)), check(3)), List.of(candidate));
        assertThatThrownBy(() -> validator.validate(mapper.writeValueAsString(omitted), input, Set.of(4L), "ZH_TO_JA"))
                .hasMessageContaining("不完整或不合法");
    }

    @Test
    void mockProviderUsesSameChecksContractWithoutClaimingCorrectionAbility() {
        var prompt = new AiChatImportPromptBuilder(mapper).build(messages,
                List.of(new AiErrorTypeOptionDTO(4L, "GRAMMAR", "语法", "说明", "LANGUAGE", "语言")), "ZH_TO_JA");
        var result = validator.validate(new MockAiChatImportClient(mapper).analyze(prompt), messages, Set.of(4L), "ZH_TO_JA");
        assertThat(result.checkedUserMessageIds()).containsExactly(1, 3);
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void identifiesMissingSectionsMessageCountsAndIncorrectOrder() {
        assertThatThrownBy(() -> validator.validate("{}", messages, Set.of(4L), "ZH_TO_JA"))
                .hasMessageContaining("缺少逐条消息检查记录（messageChecks）");
        assertThatThrownBy(() -> validator.validate("{\"messageChecks\":[]}", messages, Set.of(4L), "ZH_TO_JA"))
                .hasMessageContaining("缺少候选列表（candidates）");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(check(1)), List.of())))
                .hasMessageContaining("应为 2 条，实际为 1 条");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(check(3), check(1)), List.of())))
                .hasMessageContaining("第 1 条检查记录的消息编号或顺序不正确，应对应用户消息 #1");
    }

    @Test
    void identifiesFindingIndexAndBothDirectionsOfEvidenceMismatch() {
        var candidate = candidate(1, "来た前に", 4L);
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(
                check(1, new Finding("来た前に", 1)), check(3)), List.of(candidate))))
                .hasMessageContaining("用户消息 #1的第 1 条发现的 candidateIndex 无效");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(
                check(1, new Finding("日本に来た前に", 0)), check(3)), List.of(candidate))))
                .hasMessageContaining("用户消息 #1 的检查发现与第 1 个候选的引用不一致");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(List.of(check(1), check(3)), List.of(candidate))))
                .hasMessageContaining("第 1 个候选引用的用户消息 #1 引用未列入检查发现");
    }

    @Test
    void identifiesInvalidEvidenceAndDuplicateFocusWithoutIncludingTheirContent() {
        var checks = List.of(check(1, new Finding("来た前に", 0)), check(3));
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(candidate(1, "捏造", 4L)))))
                .hasMessageContaining("第 1 个候选的第 1 条引用与用户消息 #1 的原文不一致");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(candidate(2, "来る前に", 4L)))))
                .hasMessageContaining("第 1 个候选的第 1 条引用未引用有效用户消息");
        var candidate = candidate(1, "来た前に", 4L);
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(candidate, candidate))))
                .hasMessageContaining("第 2 个候选的分类和重点名称与前面的候选重复");
    }

    @Test
    void identifiesCandidateFieldLimitsAndLanguageRequirements() {
        var checks = List.of(check(1, new Finding("来た前に", 0)), check(3));
        var tooLong = candidateWithTexts("前に的接续", "说".repeat(256), "来日本之前学习过。", "日本に来る前に勉強しました。");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(tooLong))))
                .hasMessageContaining("第 1 个候选的解释不能超过 255 个字符");
        var blank = candidateWithTexts(" ", "使用辞书形", "来日本之前学习过。", "日本に来る前に勉強しました。");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(blank))))
                .hasMessageContaining("第 1 个候选的重点名称不能为空");
        var japaneseSource = candidateWithTexts("前に的接续", "使用辞书形", "ありがとう。", "日本に来る前に勉強しました。");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(japaneseSource))))
                .hasMessageContaining("第 1 个候选：复习题面必须使用当前学习模式的中文或英文");
        var answerWithoutKana = candidateWithTexts("前に的接续", "使用辞书形", "来日本之前学习过。", "日本");
        assertThatThrownBy(() -> validate(new AiChatImportAnalysisDTO(checks, List.of(answerWithoutKana))))
                .hasMessageContaining("第 1 个候选：日语参考答案必须包含日语假名");
        var englishMode = candidateWithTexts("Before", "Use dictionary form", "在来日本之前。", "日本に来る前に勉強しました。");
        assertThatThrownBy(() -> validator.validate(mapper.writeValueAsString(new AiChatImportAnalysisDTO(
                checks, List.of(englishMode))), messages, Set.of(4L), "EN_TO_JA"))
                .hasMessageContaining("第 1 个候选：复习题面必须使用当前学习模式的中文或英文");
    }

    @Test
    void acceptsKanaHintsInChineseAndEnglishReviewPrompts() {
        var checks = List.of(check(1, new Finding("来た前に", 0)), check(3));
        for (String source : List.of("他仍然在袒护芙丽达。（人名读音：フリーダ。）",
                "奥尔加一家住在这个村子里。（奥尔加的读音：オルガ。）", "来她家之前学习过。（读音：かのじょ。）")) {
            var candidate = candidateWithTexts("学习重点", "说明", source, "彼は今もフリーダをかばっている。");
            assertThat(validate(new AiChatImportAnalysisDTO(checks, List.of(candidate))).candidates()).containsExactly(candidate);
        }
        var english = candidateWithTexts("Name spelling", "Use the correct spelling",
                "He still defends Frieda. (Name: フリーダ.)", "彼は今もフリーダをかばっている。");
        assertThat(validator.validate(mapper.writeValueAsString(new AiChatImportAnalysisDTO(checks, List.of(english))),
                messages, Set.of(4L), "EN_TO_JA").candidates()).containsExactly(english);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void diagnosticsDoNotExposeChatAiFieldsOrParserExceptions(CapturedOutput output) {
        var parseFailure = catchThrowableOfType(() -> validator.validate("{\"私密模型输出\":", messages,
                Set.of(4L), "ZH_TO_JA"), BusinessException.class);
        assertThat(parseFailure).hasMessageContaining("JSON 格式或字段类型不符合约定").hasNoCause();
        assertThat(parseFailure.getCode()).isEqualTo(ErrorCode.BUSINESS_ERROR.getCode());

        var candidate = candidateWithTexts("私密重点", "私密解释", "テスト", "私密答案です。");
        var languageFailure = catchThrowableOfType(() -> validate(new AiChatImportAnalysisDTO(List.of(
                check(1, new Finding("来た前に", 0)), check(3)), List.of(candidate))), BusinessException.class);
        assertThat(languageFailure).hasMessageContaining("第 1 个候选：复习题面").hasNoCause();
        assertThat(languageFailure.getCode()).isEqualTo(ErrorCode.BUSINESS_ERROR.getCode());

        assertThat(parseFailure.getMessage() + languageFailure.getMessage() + output.getAll())
                .doesNotContain("私密模型输出", "私密重点", "私密解释", "敏感题面", "私密答案", "日本に来た前に勉強しました。", "来た前に");
        assertThat(output.getAll()).contains("聊天分析结果校验失败", "JSON 格式或字段类型不符合约定", "第 1 个候选：复习题面");
    }

    private ChatImportAnalysisVO.Candidate candidateWithTexts(String name, String description, String source, String answer) {
        return new ChatImportAnalysisVO.Candidate(4L, name, description, "MEDIUM",
                List.of(new ChatImportAnalysisVO.Evidence(1, "来た前に")), "来る前に", source, answer);
    }

    private MessageCheck check(int messageId, Finding... findings) {
        return new MessageCheck(messageId, List.of(findings));
    }

    private ChatImportAnalysisVO validate(AiChatImportAnalysisDTO result) {
        return validator.validate(mapper.writeValueAsString(result), messages, Set.of(4L), "ZH_TO_JA");
    }

    private ChatImportAnalysisVO.Candidate candidate(int id, String original, Long type) {
        return new ChatImportAnalysisVO.Candidate(type, "前に的接续", "使用辞书形", "MEDIUM",
                List.of(new ChatImportAnalysisVO.Evidence(id, original)), "来る前に", "来日本之前学习过。", "日本に来る前に勉強しました。");
    }
}
