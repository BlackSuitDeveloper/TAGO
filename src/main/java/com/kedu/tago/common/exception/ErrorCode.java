package com.kedu.tago.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API 에러 코드 (1000 공통, 2000 인증/인가, 3000 회원, 4000 운행/좌석, 5000 예매/결제, 6000 대기열, 9000 서버) */
@Getter
public enum ErrorCode {

    BAD_REQUEST(1000, HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    MISSING_REQUIRED_FIELD(1001, HttpStatus.BAD_REQUEST, "필수 정보가 누락되었습니다."),
    PATH_NOT_FOUND(1002, HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(1003, HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(1004, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    DATA_CONFLICT(1005, HttpStatus.CONFLICT, "이미 존재하거나 처리할 수 없는 데이터입니다."),

    LOGIN_REQUIRED(2000, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(2001, HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    LOGIN_FAILED(2002, HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(2003, HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ADMIN_ONLY(2004, HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다."),
    NOT_RESOURCE_OWNER(2005, HttpStatus.FORBIDDEN, "본인의 정보만 처리할 수 있습니다."),
    LOGIN_LOCKED_ACCOUNT(2006, HttpStatus.TOO_MANY_REQUESTS, "비밀번호 5회 연속 오류로 계정이 일시 잠금되었습니다. 잠시 후 다시 시도해 주세요."),
    LOGIN_LOCKED_IP(2007, HttpStatus.TOO_MANY_REQUESTS, "비정상적인 로그인 시도가 감지되어 해당 IP에서의 접속이 일시 차단되었습니다. 잠시 후 다시 시도해 주세요."),

    MEMBER_NOT_FOUND(3000, HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),
    MEMBER_INFO_MISMATCH(3001, HttpStatus.NOT_FOUND, "입력하신 정보와 일치하는 회원 정보를 찾을 수 없습니다."),
    SIGNUP_FAILED(3002, HttpStatus.INTERNAL_SERVER_ERROR, "회원가입에 실패했습니다."),
    EMAIL_NOT_REGISTERED(3003, HttpStatus.BAD_REQUEST, "회원 정보 또는 등록된 이메일을 찾을 수 없습니다."),
    NAME_EMAIL_REQUIRED(3004, HttpStatus.BAD_REQUEST, "이름과 이메일을 모두 입력해 주세요."),
    ID_EMAIL_REQUIRED(3005, HttpStatus.BAD_REQUEST, "아이디와 이메일을 모두 입력해 주세요."),
    EMAIL_CODE_REQUIRED(3006, HttpStatus.BAD_REQUEST, "이메일과 인증번호를 입력해 주세요."),
    CODE_REQUIRED(3007, HttpStatus.BAD_REQUEST, "인증번호를 입력해 주세요."),
    CODE_MISMATCH(3010, HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않거나 만료되었습니다."),
    VERIFICATION_EXPIRED(3011, HttpStatus.BAD_REQUEST, "인증 세션이 만료되었습니다. 처음부터 다시 시도해 주세요."),
    EMAIL_VERIFICATION_REQUIRED(3012, HttpStatus.BAD_REQUEST, "이메일 인증이 완료되지 않았거나 인증 세션이 만료되었습니다. 다시 인증해 주세요."),
    DUPLICATE_EMAIL(3013, HttpStatus.CONFLICT, "이미 가입된 이메일 주소입니다."),
    EMAIL_SEND_COOLDOWN(3014, HttpStatus.TOO_MANY_REQUESTS, "인증번호를 이미 발송했습니다. 1분 후 다시 시도해 주세요."),
    VERIFICATION_ATTEMPTS_EXCEEDED(3015, HttpStatus.BAD_REQUEST, "인증번호 입력 시도 횟수(5회)를 초과했습니다. 인증번호를 다시 요청해 주세요."),
    MAIL_SEND_FAILED(3016, HttpStatus.INTERNAL_SERVER_ERROR, "이메일 발송에 실패했습니다. 메일 서버 설정을 확인하거나 잠시 후 다시 시도해 주세요."),
    PASSWORD_TOO_SHORT(3020, HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자리 이상이어야 합니다."),
    PASSWORD_CHANGE_FAILED(3021, HttpStatus.INTERNAL_SERVER_ERROR, "비밀번호 변경에 실패했습니다."),

    SCHEDULE_NOT_FOUND(4000, HttpStatus.NOT_FOUND, "존재하지 않는 운행 일정입니다."),
    SEAT_NOT_SELECTED(4001, HttpStatus.BAD_REQUEST, "운행 정보, 호차 및 좌석을 선택해 주세요."),
    SEAT_NOT_FOUND(4002, HttpStatus.NOT_FOUND, "좌석 정보를 찾을 수 없습니다."),
    SEAT_ALREADY_TAKEN(4003, HttpStatus.CONFLICT, "이미 선택되었거나 예약이 진행 중인 좌석입니다."),

    RESERVATION_NOT_FOUND(5000, HttpStatus.NOT_FOUND, "예매 내역을 찾을 수 없습니다."),
    RESERVATION_ALREADY_CANCELLED(5002, HttpStatus.BAD_REQUEST, "이미 취소된 예매입니다."),
    HOLD_EXPIRED(5011, HttpStatus.GONE, "좌석 선점 유효시간이 만료되었습니다. 다시 예매해 주세요."),
    PAYMENT_AMOUNT_INVALID(5012, HttpStatus.BAD_REQUEST, "결제 요청 금액이 유효하지 않습니다."),
    PAYMENT_DECLINED(5013, HttpStatus.PAYMENT_REQUIRED, "결제가 승인되지 않았습니다. 선점한 좌석은 반납되었습니다."),
    IDEMPOTENCY_KEY_REQUIRED(5014, HttpStatus.BAD_REQUEST, "결제 요청에는 Idempotency-Key 헤더가 필요합니다."),
    PAYMENT_IN_PROGRESS(5015, HttpStatus.CONFLICT, "같은 결제 요청을 처리하고 있습니다. 잠시 후 결과를 확인해 주세요."),
    HOLD_BUSY(5016, HttpStatus.CONFLICT, "좌석 확인이 잠시 지연되고 있습니다. 잠시 후 다시 결제해 주세요."),

    QUEUE_TOKEN_REQUIRED(6000, HttpStatus.BAD_REQUEST, "대기열 입장이 필요합니다. 예매를 처음부터 다시 진행해 주세요."),
    QUEUE_TOKEN_INVALID(6001, HttpStatus.FORBIDDEN, "대기열 입장 시간이 만료되었습니다. 다시 시도해 주세요."),
    QUEUE_NOT_ADMITTED(6002, HttpStatus.FORBIDDEN, "아직 대기열 입장 차례가 아닙니다. 잠시만 기다려 주세요."),
    QUEUE_SERVICE_UNAVAILABLE(6003, HttpStatus.SERVICE_UNAVAILABLE, "접속자가 많아 대기열 확인이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."),

    INTERNAL_ERROR(9000, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
