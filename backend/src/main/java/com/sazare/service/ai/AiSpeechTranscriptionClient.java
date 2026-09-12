package com.sazare.service.ai;

public interface AiSpeechTranscriptionClient {
    String transcribe(byte[] audio, String mimeType);
}
