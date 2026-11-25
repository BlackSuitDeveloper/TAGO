package com.kedu.tago.queue;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * 대기열 줄 서기·순번 조회 (사용자 쪽). Redis 가 없으면 거절한다 (Fail-closed).
 *
 * 줄을 서기만 하고 입장은 시키지 않는다. 입장은 QueueScheduler 가 1초마다 처리한다 (QueueAdmissionService).
 * 조회는 "대기 순번 → 입장 키" 순서로 본다. 입장 처리가 "입장 키 생성 → 대기열에서 제거" 순서라서,
 * 두 작업이 겹쳐도 토큰이 대기열과 입장 어느 쪽에서도 안 보이는 순간이 생기지 않는다.
 */
@Slf4j
@Service
public class QueueService {

    private final StringRedisTemplate redisTemplate;

    public QueueService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 대기열 진입 (회원+열차별 멱등 진입) */
    public QueueResponseDTO enterQueue(Long scheduleSeq, String memberId) {
        if (scheduleSeq == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        if (memberId == null || memberId.isBlank()) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }

        StringRedisTemplate redis = requireRedis();
        long now = System.currentTimeMillis();
        String schedule = String.valueOf(scheduleSeq);
        String userKey = QueueKeys.user(schedule, memberId);

        try {
            // 1. 기존 유효 토큰이 있으면 멱등 반환 (중복 진입 방지)
            String existingToken = redis.opsForValue().get(userKey);
            if (existingToken != null && !existingToken.isBlank()) {
                QueueResponseDTO existing = currentStatus(redis, schedule, memberId, existingToken, now, "즉시 예매 가능합니다.");
                if (existing != null) {
                    return existing;
                }
                redis.delete(userKey); // 만료된 토큰 매핑 정리
            }

            // 2. 신규 토큰 발급: 메타를 먼저 저장(스케줄러 유령 오판 방지)
            String token = UUID.randomUUID().toString();
            QueueTokens.saveMeta(redis, token, memberId, schedule, now);

            // 3. 회원+열차 매핑은 하나만: 동시에 두 번 진입하면 먼저 쓴 토큰을 함께 쓴다
            Boolean mine = redis.opsForValue().setIfAbsent(userKey, token, QueueKeys.META_TTL);
            if (!Boolean.TRUE.equals(mine)) {
                redis.delete(QueueKeys.meta(token));
                String winner = redis.opsForValue().get(userKey);
                if (winner == null) {
                    throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
                }
                QueueResponseDTO reused = currentStatus(redis, schedule, memberId, winner, now, "즉시 예매 가능합니다.");
                return reused != null ? reused : waiting(winner, null); // 상대가 아직 줄을 서는 중
            }

            // 4. 줄 서기 + 스케줄러가 볼 운행 목록에 등록
            redis.opsForZSet().addIfAbsent(QueueKeys.waiting(schedule), token, now);
            redis.opsForSet().add(QueueKeys.SCHEDULES, schedule);

            return waiting(token, redis.opsForZSet().rank(QueueKeys.waiting(schedule), token));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis enterQueue failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 대기열 순번 조회 (폴링용, 생존 신호 갱신) */
    public QueueResponseDTO checkQueueRank(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new ApiException(ErrorCode.QUEUE_TOKEN_REQUIRED);
        }

        StringRedisTemplate redis = requireRedis();
        String metaKey = QueueKeys.meta(queueToken);

        try {
            Map<Object, Object> meta = redis.opsForHash().entries(metaKey);
            if (meta == null || meta.isEmpty() || meta.get("scheduleSeq") == null) {
                throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
            }

            String schedule = String.valueOf(meta.get("scheduleSeq"));
            String memberId = meta.get("memberId") != null ? String.valueOf(meta.get("memberId")) : null;

            QueueResponseDTO status = currentStatus(redis, schedule, memberId, queueToken, System.currentTimeMillis(),
                    "입장 차례입니다. 예매를 진행해 주세요.");
            if (status != null) {
                return status;
            }

            // 입장 시간이 끝났거나 유령 토큰으로 정리된 경우 메타/유저 매핑 정리 후 예외
            redis.delete(metaKey);
            if (memberId != null) {
                redis.delete(QueueKeys.user(schedule, memberId));
            }
            throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis checkQueueRank failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 토큰의 현재 상태 (대기 순번 → 입장 키 순서로 확인). 대기 중이거나 입장 상태면 생존 신호를 갱신한다.
     * 둘 다 아니면 null.
     */
    private QueueResponseDTO currentStatus(
            StringRedisTemplate redis, String schedule, String memberId, String token, long now, String admittedMessage) {
        Long rank = redis.opsForZSet().rank(QueueKeys.waiting(schedule), token);
        if (rank != null) {
            QueueTokens.touch(redis, schedule, memberId, token, now);
            redis.opsForSet().add(QueueKeys.SCHEDULES, schedule); // 스케줄러 목록에서 빠졌어도 다시 넣는다
            return waiting(token, rank);
        }
        if (Boolean.TRUE.equals(redis.hasKey(QueueKeys.active(schedule, token)))) {
            QueueTokens.touch(redis, schedule, memberId, token, now);
            return admitted(token, admittedMessage);
        }
        return null;
    }

    private StringRedisTemplate requireRedis() {
        if (redisTemplate == null) {
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        return redisTemplate;
    }

    private static QueueResponseDTO admitted(String token, String message) {
        return QueueResponseDTO.builder()
                .queueToken(token)
                .queueRank(0L)
                .isAvailable(true)
                .message(message)
                .build();
    }

    private static QueueResponseDTO waiting(String token, Long rank) {
        return QueueResponseDTO.builder()
                .queueToken(token)
                .queueRank(rank != null ? rank + 1 : 1L)
                .isAvailable(false)
                .message("대기 중입니다.")
                .build();
    }
}
