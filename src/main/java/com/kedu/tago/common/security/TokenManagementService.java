package com.kedu.tago.common.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.kedu.tago.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/** Redis 기반 JWT 토큰 무효화 및 블랙리스트 관리 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenManagementService {

    private static final String BLACKLIST_PREFIX = "BLACKLIST:TOKEN:";
    private static final String INVALIDATION_TIME_PREFIX = "MEMBER:MIN_VALID_TIME:";

    private final RedisConfig redisConfig;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration:3600000}")
    private Long jwtExpiration;

    /** 특정 토큰을 블랙리스트에 등록 (로그아웃 처리) */
    public void blacklistToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return;
        }
        try {
            DecodedJWT jwt = jwtUtil.verifyToken(rawToken.trim());
            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt != null) {
                long remainMs = expiresAt.getTime() - System.currentTimeMillis();
                if (remainMs > 0) {
                    redisConfig.setValue(BLACKLIST_PREFIX + rawToken.trim(), "LOGOUT", remainMs, TimeUnit.MILLISECONDS);
                    log.info("🚫 JWT 토큰 블랙리스트 등록 완료 (남은 시간: {}ms, 대상 계정: {})", remainMs, jwt.getSubject());
                }
            }
        } catch (Exception e) {
            log.debug("만료되었거나 유효하지 않은 토큰은 블랙리스트 등록 생략: {}", e.getMessage());
        }
    }

    /** 토큰이 블랙리스트(로그아웃 등)에 포함되어 있는지 검사 */
    public boolean isBlacklisted(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            return false;
        }
        return redisConfig.hasKey(BLACKLIST_PREFIX + rawToken.trim());
    }

    /** 회원의 기존 발급 토큰을 일괄 무효화 (비밀번호 변경, 회원 탈퇴 시 호출) */
    public void invalidateMemberTokens(String memberId) {
        if (memberId == null || memberId.trim().isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        // 토큰 최대 수명(기본 1시간) + 5분 여유 시간 동안 보관
        long ttlMs = (jwtExpiration != null ? jwtExpiration : 3600000L) + 300000L;
        redisConfig.setValue(INVALIDATION_TIME_PREFIX + memberId.trim(), String.valueOf(now), ttlMs, TimeUnit.MILLISECONDS);
        log.info("🔄 회원의 이전 토큰 일괄 무효화 기준시각 설정 완료 (계정: {}, 기준시각: {})", memberId, now);
    }

    /** 토큰 발급 시각이 무효화 기준시각 이전인지 검사 (비밀번호 변경/탈퇴 후 재사용 차단) */
    public boolean isTokenInvalidated(String memberId, Date issuedAt) {
        if (memberId == null || issuedAt == null) {
            return false;
        }
        String minValidTimeStr = redisConfig.getValue(INVALIDATION_TIME_PREFIX + memberId.trim());
        if (minValidTimeStr != null) {
            try {
                long minValidTime = Long.parseLong(minValidTimeStr);
                // 발급 시각이 무효화 기준 시각보다 이전이면 무효
                return issuedAt.getTime() < minValidTime;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
