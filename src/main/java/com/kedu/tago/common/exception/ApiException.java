package com.kedu.tago.common.exception;

import lombok.Getter;

/**
 * 컨트롤러·서비스·인터셉터에서 에러 응답을 만들 때 던진다. 에러 종류는 ErrorCode 로 고른다.
 * GlobalExceptionHandler 가 받아 { success:false, status, code, message } 로 응답한다.
 *
 * <pre>
 * throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
 * throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 3A 은(는) 이미 선택되었습니다.");
 * </pre>
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 기본 문구 사용 */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 상황에 맞는 문구로 바꿔서 사용 (코드·HTTP 상태는 그대로) */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
