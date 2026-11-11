package com.sazare.service.impl;

import com.sazare.config.AiProperties;
import com.sazare.service.SpeechTranscriptionSessionService;
import com.sazare.service.ai.client.GoogleAiLiveSessionClient;
import com.sazare.vo.SpeechTranscriptionSessionVO;
import com.sazare.vo.SpeechTranscriptionConfigVO;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SpeechTranscriptionSessionServiceImpl implements SpeechTranscriptionSessionService {
    private final AiProperties properties;
    private final GoogleAiLiveSessionClient client;

    public SpeechTranscriptionSessionServiceImpl(AiProperties properties, GoogleAiLiveSessionClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public SpeechTranscriptionConfigVO getConfig() {
        boolean live = properties.getSpeechTranscriptionMode() == AiProperties.SpeechTranscriptionMode.LIVE;
        var google = properties.getProviders().getGoogle();
        return new SpeechTranscriptionConfigVO(live ? "live" : "file",
                live ? google.getLiveTranscriptionModel() : google.getTranscriptionModel());
    }

    @Override
    public SpeechTranscriptionSessionVO createSession() {
        if (properties.getSpeechTranscriptionMode() != AiProperties.SpeechTranscriptionMode.LIVE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前配置为文件转写，请重新开始录音");
        }
        if ("mock".equalsIgnoreCase(properties.getProvider().trim())) {
            return new SpeechTranscriptionSessionVO("", "", Map.of(), true);
        }
        return client.createSession();
    }
}
