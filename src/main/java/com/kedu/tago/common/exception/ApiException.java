package com.kedu.tago.common.exception;

import lombok.Getter;

/** ErrorCode 기반 API 예외 (GlobalExceptionHandler 가 에러 응답으로 변환) */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** ErrorCode 기본 문구 사용 */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 문구만 상황에 맞게 변경 (코드·HTTP 상태는 유지) */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
