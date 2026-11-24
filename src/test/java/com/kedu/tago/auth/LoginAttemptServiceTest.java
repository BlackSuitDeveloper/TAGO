package com.kedu.tago.auth;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.config.RedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private RedisConfig redisConfig;

    private LoginAttemptService loginAttemptService;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(redisConfig);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
    }

    @Test
    @DisplayName("IP가 잠금 상태이면 로그인 시도 전 LOGIN_LOCKED_IP 예외 발생")
    void checkLoginLock_whenIpLocked_throwsException() {
        when(redisConfig.hasKey("LOGIN:LOCK:IP:127.0.0.1")).thenReturn(true);
        when(redisConfig.getExpire("LOGIN:LOCK:IP:127.0.0.1", TimeUnit.SECONDS)).thenReturn(300L);

        ApiException ex = assertThrows(ApiException.class, () ->
                loginAttemptService.checkLoginLock("user1", request));
        assertEquals(ErrorCode.LOGIN_LOCKED_IP, ex.getErrorCode());
    }

    @Test
    @DisplayName("계정이 잠금 상태이면 로그인 시도 전 LOGIN_LOCKED_ACCOUNT 예외 발생")
    void checkLoginLock_whenAccountLocked_throwsException() {
        when(redisConfig.hasKey("LOGIN:LOCK:IP:127.0.0.1")).thenReturn(false);
        when(redisConfig.hasKey("LOGIN:LOCK:ID:user1")).thenReturn(true);
        when(redisConfig.getExpire("LOGIN:LOCK:ID:user1", TimeUnit.SECONDS)).thenReturn(600L);

        ApiException ex = assertThrows(ApiException.class, () ->
                loginAttemptService.checkLoginLock("user1", request));
        assertEquals(ErrorCode.LOGIN_LOCKED_ACCOUNT, ex.getErrorCode());
    }

    @Test
    @DisplayName("계정 실패 횟수가 5회에 도달하면 잠금 설정 및 LOGIN_LOCKED_ACCOUNT 예외 발생")
    void processLoginFailure_reachesMaxAccountFailures_locksAccount() {
        when(redisConfig.increment("LOGIN:FAIL:IP:127.0.0.1")).thenReturn(1L);
        when(redisConfig.increment("LOGIN:FAIL:ID:user1")).thenReturn(5L);

        ApiException ex = assertThrows(ApiException.class, () ->
                loginAttemptService.processLoginFailure("user1", request));

        assertEquals(ErrorCode.LOGIN_LOCKED_ACCOUNT, ex.getErrorCode());
        verify(redisConfig).setValue(eq("LOGIN:LOCK:ID:user1"), eq("LOCKED"), eq(15L), eq(TimeUnit.MINUTES));
        verify(redisConfig).deleteValue("LOGIN:FAIL:ID:user1");
    }

    @Test
    @DisplayName("계정 실패 횟수가 5회 미만이면 남은 시도 횟수를 안내하고 LOGIN_FAILED 예외 발생")
    void processLoginFailure_underMaxAccountFailures_showsRemainingAttempts() {
        when(redisConfig.increment("LOGIN:FAIL:IP:127.0.0.1")).thenReturn(1L);
        when(redisConfig.increment("LOGIN:FAIL:ID:user1")).thenReturn(2L);

        ApiException ex = assertThrows(ApiException.class, () ->
                loginAttemptService.processLoginFailure("user1", request));

        assertEquals(ErrorCode.LOGIN_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("남은 시도: 3회"));
    }

    @Test
    @DisplayName("로그인 성공 시 실패 기록 및 잠금 키 삭제")
    void processLoginSuccess_clearsFailuresAndLocks() {
        loginAttemptService.processLoginSuccess("user1", request);

        verify(redisConfig).deleteValue("LOGIN:FAIL:ID:user1");
        verify(redisConfig).deleteValue("LOGIN:LOCK:ID:user1");
        verify(redisConfig).deleteValue("LOGIN:FAIL:IP:127.0.0.1");
    }

    @Test
    @DisplayName("비밀번호 재설정 성공 시 계정 잠금 해제")
    void unlockAccount_clearsAccountLocks() {
        loginAttemptService.unlockAccount("user1");

        verify(redisConfig).deleteValue("LOGIN:FAIL:ID:user1");
        verify(redisConfig).deleteValue("LOGIN:LOCK:ID:user1");
    }
}
