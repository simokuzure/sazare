package com.jt.learning.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;

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

}
