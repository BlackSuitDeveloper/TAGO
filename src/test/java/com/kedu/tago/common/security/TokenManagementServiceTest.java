package com.kedu.tago.common.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.config.RedisConfig;
import com.kedu.tago.member.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenManagementServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123";

    private RedisConfig redisConfig;
    private JwtUtil jwtUtil;
    private TokenManagementService tokenManagementService;

    @BeforeEach
    void setUp() {
        redisConfig = mock(RedisConfig.class);
        jwtUtil = new JwtUtil(3_600_000L, SECRET);
        tokenManagementService = new TokenManagementService(redisConfig, jwtUtil);
    }

    @Test
    @DisplayName("유효한 토큰 로그아웃 시 남은 유효시간만큼 Redis 블랙리스트에 등록된다")
    void blacklistToken_success() {
        String token = jwtUtil.createToken("user1", Role.USER);

        tokenManagementService.blacklistToken(token);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);

        verify(redisConfig).setValue(keyCaptor.capture(), valCaptor.capture(), ttlCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        assertEquals("BLACKLIST:TOKEN:" + token, keyCaptor.getValue());
        assertEquals("LOGOUT", valCaptor.getValue());
        assertTrue(ttlCaptor.getValue() > 0 && ttlCaptor.getValue() <= 3_600_000L);
    }

    @Test
    @DisplayName("null 또는 빈 문자열 토큰은 블랙리스트 등록하지 않는다")
    void blacklistToken_nullOrEmpty() {
        tokenManagementService.blacklistToken(null);
        tokenManagementService.blacklistToken("   ");

        verify(redisConfig, never()).setValue(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("이미 만료된 토큰은 예외 없이 블랙리스트 등록을 생략한다")
    void blacklistToken_expiredToken() {
        // 과거 시점으로 만료된 토큰 생성
        String expiredToken = JWT.create()
                .withSubject("user1")
                .withExpiresAt(new Date(System.currentTimeMillis() - 10_000L))
                .sign(Algorithm.HMAC256(SECRET));

        tokenManagementService.blacklistToken(expiredToken);

        verify(redisConfig, never()).setValue(any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("블랙리스트 조회 검사")
    void isBlacklisted() {
        when(redisConfig.hasKey("BLACKLIST:TOKEN:sample-token")).thenReturn(true);
        when(redisConfig.hasKey("BLACKLIST:TOKEN:other-token")).thenReturn(false);

        assertTrue(tokenManagementService.isBlacklisted("sample-token"));
        assertFalse(tokenManagementService.isBlacklisted("other-token"));
        assertFalse(tokenManagementService.isBlacklisted(null));
        assertFalse(tokenManagementService.isBlacklisted(""));
    }

    @Test
    @DisplayName("회원 토큰 일괄 무효화 시 현재 시각 타임스탬프를 Redis에 저장한다")
    void invalidateMemberTokens() {
        tokenManagementService.invalidateMemberTokens("testuser");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);

        verify(redisConfig).setValue(keyCaptor.capture(), valCaptor.capture(), ttlCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        assertEquals("MEMBER:MIN_VALID_TIME:testuser", keyCaptor.getValue());
        assertTrue(Long.parseLong(valCaptor.getValue()) <= System.currentTimeMillis());
        assertTrue(ttlCaptor.getValue() >= 3_600_000L);
    }

    @Test
    @DisplayName("무효화 기준시각 이전에 발급된 토큰은 isTokenInvalidated 가 true 를 반환한다")
    void isTokenInvalidated_beforeMinValidTime() {
        long baseTime = 1700000000000L;
        when(redisConfig.getValue("MEMBER:MIN_VALID_TIME:user1")).thenReturn(String.valueOf(baseTime));

        // 기준시각 이전 발급 (무효 대상)
        Date issuedBefore = new Date(baseTime - 10_000L);
        assertTrue(tokenManagementService.isTokenInvalidated("user1", issuedBefore));

        // 기준시각 이후 발급 (유효)
        Date issuedAfter = new Date(baseTime + 10_000L);
        assertFalse(tokenManagementService.isTokenInvalidated("user1", issuedAfter));
    }

    @Test
    @DisplayName("무효화 기준시각이 등록되지 않은 회원은 isTokenInvalidated 가 false 를 반환한다")
    void isTokenInvalidated_noRecord() {
        when(redisConfig.getValue("MEMBER:MIN_VALID_TIME:user1")).thenReturn(null);

        assertFalse(tokenManagementService.isTokenInvalidated("user1", new Date()));
        assertFalse(tokenManagementService.isTokenInvalidated(null, new Date()));
        assertFalse(tokenManagementService.isTokenInvalidated("user1", null));
    }
}
