package com.sazare.util;

import com.sazare.dto.ChatMessageDTO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ChatMarkdownParser {
    public static final int MAX_FILE_BYTES = 128 * 1024;
    public static final int MAX_BODY_CHARACTERS = 30_000;
    private static final Pattern ROLE = Pattern.compile("^##[ \\t]+(User|Assistant)[ \\t]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

    public List<ChatMessageDTO> parse(byte[] bytes) {
        if (bytes.length == 0) throw invalid("聊天文件不能为空");
        if (bytes.length > MAX_FILE_BYTES) throw invalid("聊天文件不能超过 128 KiB");
        String markdown;
        try {
            markdown = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw invalid("聊天文件必须使用 UTF-8 编码");
        }
        if (markdown.startsWith("\uFEFF")) markdown = markdown.substring(1);
        markdown = markdown.replace("\r\n", "\n").replace('\r', '\n');
        List<ChatMessageDTO> messages = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        String role = null;
        String fence = null;
        boolean titleSeen = false;
        for (String line : markdown.split("\n", -1)) {
            var roleMatch = ROLE.matcher(line);
            if (fence == null && roleMatch.matches()) {
                if (role != null) addMessage(messages, role, lines);
                lines.clear();
                role = roleMatch.group(1).toLowerCase(Locale.ROOT);
                continue;
            }
            if (role == null) {
                if (line.isBlank()) continue;
                if (!titleSeen && line.startsWith("# ") && !line.substring(2).isBlank()) {
                    titleSeen = true;
                    continue;
                }
                throw invalid("聊天内容必须使用 ## User 和 ## Assistant 标题分隔");
            }
            var fenceMatch = FENCE.matcher(line);
            if (fenceMatch.matches()) {
                String marker = fenceMatch.group(1);
                if (fence == null) {
                    fence = marker;
                } else if (marker.charAt(0) == fence.charAt(0)
                        && marker.length() >= fence.length() && fenceMatch.group(2).isBlank()) {
                    fence = null;
                }
            }
            lines.add(line);
        }
        if (fence != null) throw invalid("Markdown 代码围栏未闭合");
        if (role != null) addMessage(messages, role, lines);
        if (messages.stream().noneMatch(message -> "user".equals(message.role()))) {
            throw invalid("聊天记录必须包含用户消息");
        }
        int characters = messages.stream().mapToInt(message ->
                message.content().codePointCount(0, message.content().length())).sum();
        if (characters > MAX_BODY_CHARACTERS) throw invalid("聊天正文不能超过 30000 字符");
        return List.copyOf(messages);
    }

    private void addMessage(List<ChatMessageDTO> messages, String role, List<String> lines) {
        String content = String.join("\n", lines);
        if (content.isBlank()) throw invalid("聊天消息不能为空");
        messages.add(new ChatMessageDTO(messages.size() + 1, role, content));
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR, message);
    }
}
