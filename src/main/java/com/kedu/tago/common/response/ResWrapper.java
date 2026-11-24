package com.kedu.tago.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.common.paging.PageResult;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.util.List;

/** 모든 API 의 공통 응답 형식 (값이 없는 필드는 JSON 에서 제외) */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"success", "status", "code", "message", "data", "totalCount"})
public class ResWrapper<T> {

    private final boolean success;
    private final Integer status;     // 에러일 때만: HTTP 상태 코드
    private final Integer code;       // 에러일 때만: ErrorCode 번호
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
        return new ResWrapper<>(true, 200, null, message, data, null);
    }

    /** 본문 없이 안내 문구만 응답 */
    public static ResWrapper<Void> done(String message) {
        return new ResWrapper<>(true, 200, null, message, null, null);
    }

    /** 목록 응답 (totalCount 는 조건에 맞는 전체 건수) */
    public static <T> ResWrapper<List<T>> list(List<T> data, long totalCount) {
        return new ResWrapper<>(true, 200, null, null, data, totalCount);
    }

    /** 페이징 없는 목록 (totalCount 는 목록 크기) */
    public static <T> ResWrapper<List<T>> list(List<T> data) {
        return list(data, data == null ? 0 : data.size());
    }

    /** 페이징 목록 (totalCount 는 전체 건수) */
    public static <T> ResWrapper<List<T>> list(PageResult<T> page) {
        return list(page.getItems(), page.getTotalCount());
    }

    /** 에러 응답 (문구 직접 지정) */
    public static ResWrapper<Void> error(ErrorCode errorCode, String message) {
        return new ResWrapper<>(false, errorCode.getStatus().value(), errorCode.getCode(), message, null, null);
    }

    /** 에러 응답 (ErrorCode 기본 문구) */
    public static ResWrapper<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage());
    }

    /** 프레임워크 예외용 에러 응답 (HTTP 상태 유지, 코드는 대표 코드 사용) */
    public static ResWrapper<Void> error(HttpStatusCode status, ErrorCode fallback, String message) {
        return new ResWrapper<>(false, status.value(), fallback.getCode(), message, null, null);
    }
}
