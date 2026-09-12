package com.sazare.service;

import com.sazare.vo.SpeechTranscriptionVO;

public interface SpeechTranscriptionService {
    SpeechTranscriptionVO transcribe(byte[] audio, String mimeType);
}
