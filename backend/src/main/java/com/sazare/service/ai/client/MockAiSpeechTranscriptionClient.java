package com.sazare.service.ai.client;

import com.sazare.service.ai.AiSpeechTranscriptionClient;

public class MockAiSpeechTranscriptionClient implements AiSpeechTranscriptionClient {
    @Override
    public String transcribe(byte[] audio, String mimeType) {
        return "{\"text\":\"【MOCK・テスト用固定文】今日は日本語を勉強します。\"}";
    }
}
