package com.jt.learning.exception;

import org.junit.jupiter.api.Test;
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

}
