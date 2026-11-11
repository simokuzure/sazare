package com.sazare.service;

import com.sazare.vo.SpeechTranscriptionSessionVO;
import com.sazare.vo.SpeechTranscriptionConfigVO;

public interface SpeechTranscriptionSessionService {
    SpeechTranscriptionConfigVO getConfig();
    SpeechTranscriptionSessionVO createSession();
}
