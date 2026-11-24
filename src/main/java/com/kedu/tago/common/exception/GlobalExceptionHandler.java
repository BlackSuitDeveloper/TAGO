package com.kedu.tago.common.exception;

import com.kedu.tago.common.response.ResWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 예외를 공통 에러 응답 형식으로 변환하는 전역 예외 처리기 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResWrapper<Void> handleApi(ApiException e, HttpServletResponse res) {
        ErrorCode code = e.getErrorCode();
        res.setStatus(code.getStatus().value());
        return ResWrapper.error(code, e.getMessage());
    }

    /** ResponseStatusException 은 HTTP 상태를 유지하고 대표 에러 코드를 붙인다 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResWrapper<Void> handleResponseStatus(ResponseStatusException e, HttpServletResponse res) {
        HttpStatusCode status = e.getStatusCode();
        res.setStatus(status.value());
        ErrorCode fallback = status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
        String message = e.getReason() != null ? e.getReason() : defaultMessage(status);
        return ResWrapper.error(status, fallback, message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResWrapper<Void> handleBadRequest(Exception e, HttpServletResponse res) {
        log.debug("잘못된 요청: {}", e.getMessage());
        return fail(res, ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResWrapper<Void> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletResponse res) {
        return fail(res, ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResWrapper<Void> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e, HttpServletResponse res) {
        return fail(res, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResWrapper<Void> handleNotFound(NoResourceFoundException e, HttpServletResponse res) {
        return fail(res, ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResWrapper<Void> handleConflict(DataIntegrityViolationException e, HttpServletResponse res) {
        log.warn("데이터 무결성 위반: {}", e.getMostSpecificCause().getMessage());
        return fail(res, ErrorCode.DATA_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResWrapper<Void> handleUnexpected(Exception e, HttpServletResponse res) {
        log.error("처리되지 않은 예외", e);
        if (res.isCommitted()) {
            // SSE 등 이미 응답이 시작된 요청은 본문을 쓸 수 없다
            return null;
        }
        return fail(res, ErrorCode.INTERNAL_ERROR);
    }

    private ResWrapper<Void> fail(HttpServletResponse res, ErrorCode code) {
        res.setStatus(code.getStatus().value());
        return ResWrapper.error(code);
    }

    private String defaultMessage(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "요청을 처리할 수 없습니다.";
    }
}
