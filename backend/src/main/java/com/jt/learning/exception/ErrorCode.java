package com.jt.learning.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    PARAM_ERROR(40001, "参数错误"),
    BUSINESS_ERROR(40002, "业务处理失败"),
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String message;
}

