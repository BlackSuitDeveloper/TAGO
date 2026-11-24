package com.kedu.tago.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 에러 코드 모음. 종류별로 번호 대역을 나눠 쓴다.
 *
 * <pre>
 * 1000번대  공통      요청 형식, 경로, 데이터 충돌
 * 2000번대  인증/인가  로그인, 토큰, 권한
 * 3000번대  회원      회원 정보, 이메일 인증, 비밀번호
 * 4000번대  운행/좌석  운행 일정, 좌석 선택
 * 5000번대  예매/결제  예매, 취소, 결제, 선점 만료
 * 9000번대  서버      처리되지 않은 내부 오류
 * (6000 대기열, 7000 동기화/외부 API, 8000 은 필요할 때 이 대역에 추가한다)
 * </pre>
 *
 * 사용법: {@code throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN);}
 * 상황별 문구가 필요하면 {@code new ApiException(ErrorCode.SEAT_ALREADY_TAKEN, "좌석 3A 은(는) ...")}.
 * 응답에는 { success:false, status(HTTP), code(여기 번호), message } 가 나간다.
 */
@Getter
public enum ErrorCode {

    // ===== 1000번대: 공통 =====
    BAD_REQUEST(1000, HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    MISSING_REQUIRED_FIELD(1001, HttpStatus.BAD_REQUEST, "필수 정보가 누락되었습니다."),
    PATH_NOT_FOUND(1002, HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(1003, HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    UNSUPPORTED_MEDIA_TYPE(1004, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    DATA_CONFLICT(1005, HttpStatus.CONFLICT, "이미 존재하거나 처리할 수 없는 데이터입니다."),

    // ===== 2000번대: 인증 / 인가 =====
    LOGIN_REQUIRED(2000, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(2001, HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    LOGIN_FAILED(2002, HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    FORBIDDEN(2003, HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ADMIN_ONLY(2004, HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다."),
    NOT_RESOURCE_OWNER(2005, HttpStatus.FORBIDDEN, "본인의 정보만 처리할 수 있습니다."),

    // ===== 3000번대: 회원 / 이메일 인증 =====
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
    PASSWORD_TOO_SHORT(3020, HttpStatus.BAD_REQUEST, "비밀번호는 최소 8자리 이상이어야 합니다."),
    PASSWORD_CHANGE_FAILED(3021, HttpStatus.INTERNAL_SERVER_ERROR, "비밀번호 변경에 실패했습니다."),

    // ===== 4000번대: 운행 / 좌석 =====
    SCHEDULE_NOT_FOUND(4000, HttpStatus.NOT_FOUND, "존재하지 않는 운행 일정입니다."),
    SEAT_NOT_SELECTED(4001, HttpStatus.BAD_REQUEST, "운행 정보, 호차 및 좌석을 선택해 주세요."),
    SEAT_NOT_FOUND(4002, HttpStatus.NOT_FOUND, "좌석 정보를 찾을 수 없습니다."),
    SEAT_ALREADY_TAKEN(4003, HttpStatus.CONFLICT, "이미 선택되었거나 예약이 진행 중인 좌석입니다."),

    // ===== 5000번대: 예매 / 결제 =====
    RESERVATION_NOT_FOUND(5000, HttpStatus.NOT_FOUND, "예매 내역을 찾을 수 없습니다."),
    RESERVATION_ID_REQUIRED(5001, HttpStatus.BAD_REQUEST, "예약 번호(reservationId)가 필요합니다."),
    RESERVATION_ALREADY_CANCELLED(5002, HttpStatus.BAD_REQUEST, "이미 취소된 예매입니다."),
    PAYMENT_NOT_AVAILABLE(5010, HttpStatus.BAD_REQUEST, "결제 가능한 상태가 아닙니다."),
    HOLD_EXPIRED(5011, HttpStatus.GONE, "좌석 선점 유효시간(5분)이 만료되었습니다. 다시 예매해 주세요."),
    PAYMENT_AMOUNT_INVALID(5012, HttpStatus.BAD_REQUEST, "결제 요청 금액이 유효하지 않습니다."),

    // ===== 9000번대: 서버 =====
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
