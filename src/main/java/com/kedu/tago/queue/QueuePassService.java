package com.kedu.tago.queue;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 대기열 입장권(pass) 검증·반납. 좌석 조회·선점·결제·취소 쪽에서 쓴다.
 * Redis 가 없으면 거절한다 (Fail-closed). 반납은 실패해도 TTL 로 풀리므로 예외를 삼킨다.
 */
@Slf4j
@Service
public class QueuePassService {

    private final StringRedisTemplate redisTemplate;

    @Value("${tago.queue.token-ttl-seconds:300}")
    private long tokenTtlSeconds = 300;

    public QueuePassService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 입장 유효 시간(초): 선점할 때 이만큼 더 남겨 두어, 선점이 만료돼도 좌석을 다시 고를 수 있게 한다 */
    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    /** 좌석 선점 시 입장권 검증 (본인·같은 열차·입장 상태) 및 입장 시간 연장 */
    public void validateAndExtendForHold(String queueToken, Long scheduleSeq, String memberId, long extendSeconds) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new ApiException(ErrorCode.QUEUE_TOKEN_REQUIRED);
        }
        if (scheduleSeq == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        if (memberId == null || memberId.isBlank()) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }

        StringRedisTemplate redis = requireRedis();
        String schedule = String.valueOf(scheduleSeq);
        String activeKey = QueueKeys.active(schedule, queueToken);

        try {
            // 1. 토큰 주인·열차 확인
            Map<Object, Object> meta = redis.opsForHash().entries(QueueKeys.meta(queueToken));
            if (meta == null || !memberId.equals(meta.get("memberId")) || !schedule.equals(meta.get("scheduleSeq"))) {
                throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
            }

            // 2. 입장 상태 확인 + 남은 시간이 필요한 시간보다 짧으면 연장
            Long remaining = redis.getExpire(activeKey);
            if (remaining == null || remaining == -2L) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED);
            }
            if (remaining >= 0 && remaining < extendSeconds
                    && !Boolean.TRUE.equals(redis.expire(activeKey, Duration.ofSeconds(extendSeconds)))) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED); // 확인과 연장 사이에 만료됨
            }

            // 3. 생존 신호 갱신
            QueueTokens.touch(redis, schedule, memberId, queueToken, System.currentTimeMillis());
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueuePass] Redis validateAndExtendForHold failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 좌석 조회 등 읽기 요청의 입장권 검증 (연장 없음) */
    public void validateActive(String queueToken, Long scheduleSeq) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new ApiException(ErrorCode.QUEUE_TOKEN_REQUIRED);
        }
        StringRedisTemplate redis = requireRedis();
        try {
            if (!Boolean.TRUE.equals(redis.hasKey(QueueKeys.active(scheduleSeq, queueToken)))) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueuePass] Redis validateActive failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 결제 완료·예매 취소·이탈 시 입장 자리 즉시 반납 (다음 대기자가 바로 들어올 수 있게) */
    public void releaseSlot(Long scheduleSeq, String memberId) {
        if (redisTemplate == null || scheduleSeq == null || memberId == null || memberId.isBlank()) {
            return;
        }
        try {
            String userKey = QueueKeys.user(scheduleSeq, memberId);
            String token = redisTemplate.opsForValue().get(userKey);
            if (token == null || token.isBlank()) {
                return;
            }
            redisTemplate.delete(QueueKeys.active(scheduleSeq, token));
            redisTemplate.opsForZSet().remove(QueueKeys.waiting(scheduleSeq), token);
            redisTemplate.delete(List.of(userKey, QueueKeys.meta(token)));
        } catch (Exception e) {
            log.warn("[QueuePass] Redis releaseSlot warning: {}", e.getMessage());
        }
    }

    private StringRedisTemplate requireRedis() {
        if (redisTemplate == null) {
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        return redisTemplate;
    }
}
