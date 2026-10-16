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
