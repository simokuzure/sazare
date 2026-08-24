package com.jt.learning.service.ai.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

final class GoogleAiErrorMessageBuilder {

    private static final int ERROR_DETAIL_LIMIT = 500;

    private GoogleAiErrorMessageBuilder() {
    }

    static String build(ObjectMapper objectMapper, String serviceMessage, int statusCode, String responseBody) {
        String prefix = serviceMessage + ": HTTP " + statusCode;
        if (responseBody == null || responseBody.isBlank()) {
            return prefix;
        }

        try {
            JsonNode error = child(objectMapper.readTree(responseBody), "error");
            if (error == null) {
                return prefix;
            }
            String errorStatus = textualValue(error, "status");
            String errorDetail = textualValue(error, "message");
            String details = String.join(" - ", List.of(errorStatus, errorDetail).stream()
                    .filter(value -> !value.isBlank())
                    .toList());
            return details.isBlank() ? prefix : prefix + " " + truncate(details);
        } catch (JacksonException exception) {
            return prefix;
        }
    }

    private static String textualValue(JsonNode node, String fieldName) {
        JsonNode value = child(node, fieldName);
        return value == null || !value.isTextual()
                ? ""
                : value.asString().replaceAll("\\s+", " ").trim();
    }

    private static String truncate(String value) {
        return value.length() <= ERROR_DETAIL_LIMIT ? value : value.substring(0, ERROR_DETAIL_LIMIT);
    }

    private static JsonNode child(JsonNode node, String fieldName) {
        return node == null || !node.isObject() ? null : node.get(fieldName);
    }
}
