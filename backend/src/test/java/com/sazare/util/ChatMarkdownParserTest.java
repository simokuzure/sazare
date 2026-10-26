package com.sazare.util;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ChatMarkdownParserTest {
    private final ChatMarkdownParser parser = new ChatMarkdownParser();

    @Test
    void preservesMessagesAndDoesNotTreatFencedTitlesAsRoles() {
        var messages = parse("\uFEFF# 会話\r\n\r\n## USER\r\n日本に来た前に。\r\n```md\r\n## Assistant\r\n```\r\n## assistant\r\n説明");
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().content()).isEqualTo("日本に来た前に。\n```md\n## Assistant\n```");
        assertThat(messages.getLast().id()).isEqualTo(2);
    }

    @Test
    void acceptsExportNotesSeparatorsAndIndentedRoleHeadings() {
        var messages = parse("# 翻译练习日志\n\n---\n\n导出日期：2026-10-26\n# 聊天记录\n***\n"
                + " ## Assistant\t\n例文を訳してください。\n\n  ## USER  \n日本語を勉強しています。\n\n---\n"
                + "   ## assistant\n自然な表現です。");

        assertThat(messages).hasSize(3);
        assertThat(messages.getFirst().role()).isEqualTo("assistant");
        assertThat(messages.getFirst().content()).isEqualTo("例文を訳してください。\n");
        assertThat(messages.get(1).role()).isEqualTo("user");
        assertThat(messages.get(1).content()).isEqualTo("日本語を勉強しています。\n\n---");
        assertThat(messages.getLast().id()).isEqualTo(3);
    }

    @Test
    void ignoresFencedFormatExamplesBeforeTheConversation() {
        var messages = parse("# 导出说明\n```markdown\n## User\n示例内容\n## Assistant\n示例回复\n```\n---\n"
                + "## User\n日本語を勉強しています。\n    ## Assistant\nこれはインデントされたコードです。");

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().id()).isEqualTo(1);
        assertThat(messages.getFirst().role()).isEqualTo("user");
        assertThat(messages.getFirst().content()).isEqualTo("日本語を勉強しています。\n    ## Assistant\nこれはインデントされたコードです。");
    }

    @Test
    void explainsWhenNoRoleHeadingsExistOutsideCodeFences() {
        assertThatThrownBy(() -> parse("# 导出说明\n---\n```markdown\n## User\n示例内容\n```"))
                .hasMessageContaining("未找到聊天角色标题");
        assertThatThrownBy(() -> parse("# 导出说明\n这里没有聊天消息"))
                .hasMessageContaining("未找到聊天角色标题");
    }

    @Test
    void countsUnicodeCodePointsAndAcceptsExactBodyLimit() {
        assertThat(parse("## User\n" + "あ".repeat(30_000))).hasSize(1);
        assertThat(parse("## User\n" + "😀".repeat(30_000))).hasSize(1);
        assertThatThrownBy(() -> parse("## User\n" + "あ".repeat(30_001))).hasMessageContaining("30000");
        assertThatThrownBy(() -> parse("## User\n" + "あ".repeat(15_000) + "\n## Assistant\n" + "い".repeat(15_001)))
                .hasMessageContaining("30000");
    }

    @Test
    void rejectsMalformedAndOversizedInput() {
        for (String text : new String[]{"", "本文", "## Assistant\n説明", "## User\n\n## Assistant\n説明", "## User\n```\n未完"}) {
            assertThatThrownBy(() -> parse(text)).isInstanceOf(com.sazare.exception.BusinessException.class);
        }
        assertThatThrownBy(() -> parser.parse(new byte[]{(byte) 0xc3, 0x28})).hasMessageContaining("UTF-8");
        assertThatThrownBy(() -> parser.parse(new byte[131_073])).hasMessageContaining("128 KiB");
    }

    private java.util.List<com.sazare.dto.ChatMessageDTO> parse(String markdown) {
        return parser.parse(markdown.getBytes(StandardCharsets.UTF_8));
    }
}
