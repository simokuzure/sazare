package com.sazare.vo;

import java.util.Map;

public record SpeechTranscriptionSessionVO(String token, String websocketUrl, Map<String, Object> setup, boolean mock) {
    @Override
    public String toString() {
        return "SpeechTranscriptionSessionVO[token=REDACTED, mock=" + mock + "]";
    }
}
