package com.sazare.service.ai.client;

import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;

public class AiProviderHttpException extends BusinessException {

    private final int statusCode;

    public AiProviderHttpException(int statusCode, String message) {
        super(ErrorCode.BUSINESS_ERROR, message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
