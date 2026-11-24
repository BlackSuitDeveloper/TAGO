package com.kedu.tago.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Getter;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.paging.PageResult;
import org.springframework.http.HttpStatusCode;

import java.util.List;

/**
 * 모든 API 의 공통 응답 형식. 값이 없는 필드는 JSON 에서 빠진다.
 *
 * <pre>
 * 1) 일반  ResWrapper.ok(data) / ok(data, "메시지") / done("메시지")
 *    { "success": true, "data": {...}, "message": "..." }
 *
 * 2) 목록  ResWrapper.list(items, totalCount)
 *    { "success": true, "data": [...], "totalCount": 120 }
 *
 * 3) 에러  { "success": false, "status": 409, "code": 4003, "message": "..." }
 *    직접 만들지 않고 ApiException(ErrorCode) 을 던지면 GlobalExceptionHandler 가 만든다
 * </pre>
 *
 * HTTP 상태 코드는 성공이면 200(생성은 컨트롤러에서 @ResponseStatus), 에러면 status 와 같은 값이다.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "status", "code", "message", "data", "totalCount"})
public class ResWrapper<T> {

    private final boolean success;
    private final Integer status;     // 에러일 때만: HTTP 상태 코드
    private final Integer code;       // 에러일 때만: ErrorCode 번호(1000번대, 2000번대 ...)
    private final String message;     // 안내 문구(선택) / 에러 사유
    private final T data;             // 응답 본문(없으면 생략)
    private final Long totalCount;    // 목록 응답일 때만

    private ResWrapper(boolean success, Integer status, Integer code, String message, T data, Long totalCount) {
        this.success = success;
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
        this.totalCount = totalCount;
    }

    /** 일반 응답 */
    public static <T> ResWrapper<T> ok(T data) {
        return new ResWrapper<>(true, null, null, null, data, null);
    }

    /** 일반 응답 + 안내 문구 */
    public static <T> ResWrapper<T> ok(T data, String message) {
        return new ResWrapper<>(true, null, null, message, data, null);
    }

    /** 본문 없이 안내 문구만 (수정/삭제/취소 등) */
    public static ResWrapper<Void> done(String message) {
        return new ResWrapper<>(true, null, null, message, null, null);
    }

    /** 목록 응답. totalCount 는 조건에 맞는 전체 건수(페이징이면 전체, 아니면 목록 크기) */
    public static <T> ResWrapper<List<T>> list(List<T> data, long totalCount) {
        return new ResWrapper<>(true, null, null, null, data, totalCount);
    }

    /** 페이징이 없는 목록: totalCount = 목록 크기 */
    public static <T> ResWrapper<List<T>> list(List<T> data) {
        return list(data, data == null ? 0 : data.size());
    }

    /** 페이징 목록: totalCount = 전체 건수 */
    public static <T> ResWrapper<List<T>> list(PageResult<T> page) {
        return list(page.getItems(), page.getTotalCount());
    }

    /** 에러 응답 (ErrorCode 의 번호·HTTP 상태 사용, 문구는 상황에 맞게 지정) */
    public static ResWrapper<Void> error(ErrorCode errorCode, String message) {
        return new ResWrapper<>(false, errorCode.getStatus().value(), errorCode.getCode(), message, null, null);
    }

    /** 에러 응답 (ErrorCode 의 기본 문구) */
    public static ResWrapper<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    /** ErrorCode 가 없는 프레임워크 예외용: HTTP 상태는 그대로, 코드는 대표 코드(1000/9000)로 */
    public static ResWrapper<Void> error(HttpStatusCode status, ErrorCode fallback, String message) {
        return new ResWrapper<>(false, status.value(), fallback.getCode(), message, null, null);
    }
}
