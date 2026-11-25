package com.kedu.tago.queue;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** QueueService 단위 테스트 (외부 Redis 없이 Fail-closed, 멱등성, 토큰 검증 규칙 확인) */
class QueueServiceUnitTest {

    @Test
    @DisplayName("Q-3: Redis 미연결 시 Fail-closed 로 QUEUE_SERVICE_UNAVAILABLE(503) 를 반환한다")
    void failClosedWhenRedisUnavailable() {
        QueueService service = new QueueService(null);

        ApiException ex1 = assertThrows(ApiException.class, () -> service.enterQueue(1L, "user1"));
        assertEquals(ErrorCode.QUEUE_SERVICE_UNAVAILABLE, ex1.getErrorCode());

        ApiException ex2 = assertThrows(ApiException.class, () -> service.checkQueueRank("some-token"));
        assertEquals(ErrorCode.QUEUE_SERVICE_UNAVAILABLE, ex2.getErrorCode());

        ApiException ex3 = assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("some-token", 1L, "user1", 300));
        assertEquals(ErrorCode.QUEUE_SERVICE_UNAVAILABLE, ex3.getErrorCode());
    }

    @Test
    @DisplayName("Q-5: 같은 회원·열차로 재진입하면 기존 유효 토큰을 그대로 반환한다 (멱등성)")
    @SuppressWarnings("unchecked")
    void idempotentEnterQueueReturnsExistingToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(redis.opsForHash()).thenReturn(hashOps);

        when(valueOps.get("tago:queue:user:10:userA")).thenReturn("existing-token-123");
        when(redis.hasKey("tago:queue:active:10:existing-token-123")).thenReturn(true);

        QueueService service = new QueueService(redis);
        QueueResponseDTO resp = service.enterQueue(10L, "userA");

        assertEquals("existing-token-123", resp.getQueueToken());
        assertTrue(resp.getIsAvailable());
        assertEquals(0L, resp.getQueueRank());
        verify(zSetOps, never()).addIfAbsent(anyString(), anyString(), anyDouble()); // 새로 줄 서지 않음
    }

    @Test
    @DisplayName("Q-2/Q-5: 존재하지 않거나 만료된 토큰으로 순번 조회 시 QUEUE_TOKEN_INVALID 를 반환한다")
    @SuppressWarnings("unchecked")
    void invalidOrExpiredTokenThrowsOnCheckRank() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("tago:queue:meta:unknown-token")).thenReturn(Map.of());

        QueueService service = new QueueService(redis);
        ApiException ex = assertThrows(ApiException.class, () -> service.checkQueueRank("unknown-token"));
        assertEquals(ErrorCode.QUEUE_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("Q-1: 선점 시 토큰 누락·불일치·미입장을 각각 정확히 거부하고, 정상이면 입장 시간을 연장한다")
    @SuppressWarnings("unchecked")
    void validateAndExtendRejectsInvalidStates() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("tago:queue:meta:token-1"))
                .thenReturn(Map.of("memberId", "user1", "scheduleSeq", "1", "lastSeen", "0"));
        QueueService service = new QueueService(redis);

        // 1. 토큰 누락
        ApiException missingEx = assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("  ", 1L, "user1", 300));
        assertEquals(ErrorCode.QUEUE_TOKEN_REQUIRED, missingEx.getErrorCode());

        // 2. 소유자 불일치 / 열차 불일치
        assertEquals(ErrorCode.QUEUE_TOKEN_INVALID, assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("token-1", 1L, "user2", 300)).getErrorCode());
        assertEquals(ErrorCode.QUEUE_TOKEN_INVALID, assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("token-1", 2L, "user1", 300)).getErrorCode());

        // 3. 미입장 또는 만료 (입장 키 없음)
        when(redis.getExpire("tago:queue:active:1:token-1")).thenReturn(-2L);
        assertEquals(ErrorCode.QUEUE_NOT_ADMITTED, assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("token-1", 1L, "user1", 300)).getErrorCode());

        // 4. 정상: 남은 100초 < 300초 이므로 300초로 연장
        when(redis.getExpire("tago:queue:active:1:token-1")).thenReturn(100L);
        when(redis.expire(eq("tago:queue:active:1:token-1"), any(Duration.class))).thenReturn(true);
        assertDoesNotThrow(() -> service.validateAndExtendForHold("token-1", 1L, "user1", 300));
        verify(redis).expire("tago:queue:active:1:token-1", Duration.ofSeconds(300));
    }
}
