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
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
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
        when(zSetOps.score("tago:queue:active:10", "existing-token-123"))
                .thenReturn((double) (System.currentTimeMillis() + 60_000L));

        QueueService service = new QueueService(redis);
        QueueResponseDTO resp = service.enterQueue(10L, "userA");

        assertEquals("existing-token-123", resp.getQueueToken());
        assertTrue(resp.getIsAvailable());
        assertEquals(0L, resp.getQueueRank());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any());
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
    @DisplayName("Q-1: 선점 시 토큰 누락·불일치·미입장을 각각 정확히 거부한다")
    @SuppressWarnings("unchecked")
    void validateAndExtendRejectsInvalidStates() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        QueueService service = new QueueService(redis);

        // 1. 토큰 누락
        ApiException missingEx = assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("  ", 1L, "user1", 300));
        assertEquals(ErrorCode.QUEUE_TOKEN_REQUIRED, missingEx.getErrorCode());

        // 2. 소유자·열차 불일치 (-1)
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(-1L);
        ApiException invalidEx = assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("other-user-token", 1L, "user1", 300));
        assertEquals(ErrorCode.QUEUE_TOKEN_INVALID, invalidEx.getErrorCode());

        // 3. 미입장 또는 만료 (-2)
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(-2L);
        ApiException notAdmittedEx = assertThrows(ApiException.class,
                () -> service.validateAndExtendForHold("waiting-token", 1L, "user1", 300));
        assertEquals(ErrorCode.QUEUE_NOT_ADMITTED, notAdmittedEx.getErrorCode());

        // 4. 정상 (1)
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        assertDoesNotThrow(() -> service.validateAndExtendForHold("valid-token", 1L, "user1", 300));
    }
}
