package com.sazare.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldNotWriteErrorResponseWhenClientDisconnected() {
        HttpMessageNotWritableException exception = new HttpMessageNotWritableException(
                "Could not write JSON",
                new AsyncRequestNotUsableException("ServletOutputStream failed to write")
        );

        assertThat(handler.handleException(exception)).isNull();
    }

    @Test
    void shouldReturnParameterErrorForUnsupportedContentType() {
        var response = handler.handleHttpMediaTypeNotSupportedException(
                mock(HttpMediaTypeNotSupportedException.class)
        );

        assertThat(response.code()).isEqualTo(ErrorCode.PARAM_ERROR.getCode());
        assertThat(response.message()).isEqualTo("Content-Type 必须为 application/json");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void businessExceptionWithCauseShouldLogOriginalException(CapturedOutput output) {
        IllegalStateException cause = new IllegalStateException("provider response parse failed");

        var response = handler.handleBusinessException(
                new BusinessException(ErrorCode.BUSINESS_ERROR, "Google AI 响应解析失败", cause)
        );

        assertThat(response.message()).isEqualTo("Google AI 响应解析失败");
        assertThat(output.getOut())
                .contains("Business exception")
                .contains("Google AI 响应解析失败")
                .contains("provider response parse failed");
    }

}
