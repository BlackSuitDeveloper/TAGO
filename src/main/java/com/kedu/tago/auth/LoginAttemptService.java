package com.kedu.tago.auth;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.config.RedisConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/** 로그인 시도 횟수 및 계정·IP 잠금 관리 서비스 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ACCOUNT_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 10;
    private static final long LOCKOUT_DURATION_MINUTES = 15;

    private static final String KEY_FAIL_ID = "LOGIN:FAIL:ID:";
    private static final String KEY_LOCK_ID = "LOGIN:LOCK:ID:";
    private static final String KEY_FAIL_IP = "LOGIN:FAIL:IP:";
    private static final String KEY_LOCK_IP = "LOGIN:LOCK:IP:";

    private final RedisConfig redisConfig;

    /** 로그인 시도 전 계정 및 IP 잠금 여부 검사 */
    public void checkLoginLock(String id, HttpServletRequest request) {
        String clientIp = getClientIp(request);

        // 1. IP 잠금 확인
        String ipLockKey = KEY_LOCK_IP + clientIp;
        if (redisConfig.hasKey(ipLockKey)) {
            Long remainSeconds = redisConfig.getExpire(ipLockKey, TimeUnit.SECONDS);
            long remainMinutes = (remainSeconds != null && remainSeconds > 0) ? (remainSeconds / 60 + 1) : LOCKOUT_DURATION_MINUTES;
            throw new ApiException(ErrorCode.LOGIN_LOCKED_IP,
                    "비정상적인 로그인 시도가 감지되어 해당 IP에서의 접속이 일시 차단되었습니다. (" + remainMinutes + "분 후 재시도)");
        }

        // 2. 계정 잠금 확인
        if (id != null && !id.trim().isEmpty()) {
            String accountLockKey = KEY_LOCK_ID + id.trim();
            if (redisConfig.hasKey(accountLockKey)) {
                Long remainSeconds = redisConfig.getExpire(accountLockKey, TimeUnit.SECONDS);
                long remainMinutes = (remainSeconds != null && remainSeconds > 0) ? (remainSeconds / 60 + 1) : LOCKOUT_DURATION_MINUTES;
                throw new ApiException(ErrorCode.LOGIN_LOCKED_ACCOUNT,
                        "비밀번호 5회 연속 오류로 계정이 일시 잠금되었습니다. (" + remainMinutes + "분 후 재시도 또는 비밀번호 재설정)");
            }
        }
    }

    /** 로그인 실패 처리 (계정 및 IP 카운트 증가 및 임계치 초과 시 잠금) */
    public void processLoginFailure(String id, HttpServletRequest request) {
        String clientIp = getClientIp(request);

        // 1. IP 실패 카운트 증가
        String ipFailKey = KEY_FAIL_IP + clientIp;
        Long ipFailCount = redisConfig.increment(ipFailKey);
        if (ipFailCount != null && ipFailCount == 1) {
            redisConfig.expire(ipFailKey, LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
        }

        if (ipFailCount != null && ipFailCount >= MAX_IP_FAILURES) {
            redisConfig.setValue(KEY_LOCK_IP + clientIp, "LOCKED", LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
            redisConfig.deleteValue(ipFailKey);
            throw new ApiException(ErrorCode.LOGIN_LOCKED_IP,
                    "로그인 실패 횟수 초과로 해당 IP에서의 접속이 " + LOCKOUT_DURATION_MINUTES + "분간 일시 차단되었습니다.");
        }

        // 2. 계정 실패 카운트 증가
        if (id != null && !id.trim().isEmpty()) {
            String accountFailKey = KEY_FAIL_ID + id.trim();
            Long idFailCount = redisConfig.increment(accountFailKey);
            if (idFailCount != null && idFailCount == 1) {
                redisConfig.expire(accountFailKey, LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
            }

            if (idFailCount != null && idFailCount >= MAX_ACCOUNT_FAILURES) {
                redisConfig.setValue(KEY_LOCK_ID + id.trim(), "LOCKED", LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
                redisConfig.deleteValue(accountFailKey);
                throw new ApiException(ErrorCode.LOGIN_LOCKED_ACCOUNT,
                        "비밀번호 5회 연속 오류로 계정이 " + LOCKOUT_DURATION_MINUTES + "분간 잠금되었습니다. 비밀번호 재설정을 통해 잠금을 해제할 수 있습니다.");
            }

            int remaining = (int) Math.max(0, MAX_ACCOUNT_FAILURES - (idFailCount != null ? idFailCount : 0));
            throw new ApiException(ErrorCode.LOGIN_FAILED,
                    "아이디 또는 비밀번호가 올바르지 않습니다. (남은 시도: " + remaining + "회)");
        }

        throw new ApiException(ErrorCode.LOGIN_FAILED);
    }

    /** 로그인 성공 시 실패 기록 초기화 */
    public void processLoginSuccess(String id, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        if (id != null && !id.trim().isEmpty()) {
            redisConfig.deleteValue(KEY_FAIL_ID + id.trim());
            redisConfig.deleteValue(KEY_LOCK_ID + id.trim());
        }
        redisConfig.deleteValue(KEY_FAIL_IP + clientIp);
    }

    /** 비밀번호 재설정 완료 시 계정 잠금 해제 */
    public void unlockAccount(String id) {
        if (id != null && !id.trim().isEmpty()) {
            redisConfig.deleteValue(KEY_FAIL_ID + id.trim());
            redisConfig.deleteValue(KEY_LOCK_ID + id.trim());
        }
    }

    /** 클라이언트 IP 추출 */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        String[] headers = {
                "X-Forwarded-For",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }
}
