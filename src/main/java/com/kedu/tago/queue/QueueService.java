package com.kedu.tago.queue;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Redis 기반 대기열 서비스 (Fail-closed 정책, 회원+열차별 멱등 토큰, 유령 토큰 정리) */
@Slf4j
@Service
public class QueueService {

    private final StringRedisTemplate redisTemplate;

    public static final String QUEUE_KEY_PREFIX = "tago:queue:wait:";
    public static final String ACTIVE_KEY_PREFIX = "tago:queue:active:";
    public static final String META_KEY_PREFIX = "tago:queue:meta:";
    public static final String USER_KEY_PREFIX = "tago:queue:user:";
    public static final String SCHEDULE_SET_KEY = "tago:queue:schedules";

    private static final long META_TTL_MINUTES = 30L;

    // 대기열 진입 Lua 스크립트
    // KEYS: activeKey, queueKey, scheduleSetKey
    // ARGV: token, now, expiresAt, maxActiveUsers, scheduleSeq
    // 반환: 0이면 즉시 입장, 1 이상이면 대기 순번(1부터)
    private static final DefaultRedisScript<Long> ENTER_QUEUE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local activeCount = redis.call('ZCARD', KEYS[1])
            if activeCount < tonumber(ARGV[4]) then
                redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
                return 0
            else
                redis.call('ZADD', KEYS[2], 'NX', ARGV[2], ARGV[1])
                redis.call('SADD', KEYS[3], ARGV[5])
                local rank = redis.call('ZRANK', KEYS[2], ARGV[1])
                return (rank or 0) + 1
            end
            """, Long.class);

    // 대기열 순차 입장 Lua 스크립트 (유령 토큰 스킵 포함)
    // KEYS: activeKey, queueKey
    // ARGV: now, expiresAt, maxActiveUsers, admitBatchSize, ghostCutoff, metaKeyPrefix
    private static final DefaultRedisScript<Long> ADMIT_BATCH_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local activeCount = redis.call('ZCARD', KEYS[1])
            local slots = tonumber(ARGV[3]) - activeCount
            if slots <= 0 then
                return 0
            end
            local target = math.min(slots, tonumber(ARGV[4]))
            if target <= 0 then
                return 0
            end
            local admitted = 0
            while admitted < target do
                local popped = redis.call('ZPOPMIN', KEYS[2], 1)
                if #popped == 0 then
                    break
                end
                local token = popped[1]
                local metaKey = ARGV[6] .. token
                local lastSeen = redis.call('HGET', metaKey, 'lastSeen')
                if lastSeen and tonumber(lastSeen) >= tonumber(ARGV[5]) then
                    redis.call('ZADD', KEYS[1], ARGV[2], token)
                    admitted = admitted + 1
                else
                    redis.call('DEL', metaKey)
                end
            end
            return admitted
            """, Long.class);

    // 좌석 선점 전 입장권 원자적 검증 및 만료 연장 Lua 스크립트
    // KEYS: activeKey, metaKey, userKey
    // ARGV: token, memberId, scheduleSeq, now, extendedExpiresAt, metaTtlSeconds
    // 반환: 1 성공, -1 소유자/열차 불일치 또는 없음, -2 미입장 또는 만료
    private static final DefaultRedisScript<Long> VALIDATE_AND_EXTEND_SCRIPT = new DefaultRedisScript<>("""
            local metaMember = redis.call('HGET', KEYS[2], 'memberId')
            local metaSchedule = redis.call('HGET', KEYS[2], 'scheduleSeq')
            if not metaMember or metaMember ~= ARGV[2] or metaSchedule ~= ARGV[3] then
                return -1
            end
            local score = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not score or tonumber(score) <= tonumber(ARGV[4]) then
                return -2
            end
            local newScore = math.max(tonumber(score), tonumber(ARGV[5]))
            redis.call('ZADD', KEYS[1], newScore, ARGV[1])
            redis.call('HSET', KEYS[2], 'lastSeen', ARGV[4])
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[6]))
            redis.call('EXPIRE', KEYS[3], tonumber(ARGV[6]))
            return 1
            """, Long.class);

    // 슬롯 반납 Lua 스크립트
    // KEYS: activeKey, queueKey, userKey, metaKey
    // ARGV: token
    private static final DefaultRedisScript<Long> RELEASE_SLOT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('DEL', KEYS[3])
            redis.call('DEL', KEYS[4])
            return 1
            """, Long.class);

    @Value("${tago.queue.max-active:200}")
    private long maxActiveUsers = 200;

    @Value("${tago.queue.batch-size:30}")
    private long admitBatchSize = 30;

    @Value("${tago.queue.token-ttl-seconds:300}")
    private long tokenTtlSeconds = 300;

    @Value("${tago.queue.ghost-timeout-seconds:30}")
    private long ghostTimeoutSeconds = 30;

    public QueueService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 대기열 진입 (회원+열차별 멱등 진입, 동시 입장 한도 미만이면 즉시 입장) */
    public QueueResponseDTO enterQueue(Long scheduleSeq, String memberId) {
        if (scheduleSeq == null) {
            throw new ApiException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        if (memberId == null || memberId.isBlank()) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }

        StringRedisTemplate redis = requireRedis();
        long now = System.currentTimeMillis();
        String queueKey = QUEUE_KEY_PREFIX + scheduleSeq;
        String activeKey = ACTIVE_KEY_PREFIX + scheduleSeq;
        String userKey = userKey(scheduleSeq, memberId);

        try {
            // 1. 기존 유효 토큰이 있으면 멱등 반환 (중복 진입 방지)
            String existingToken = redis.opsForValue().get(userKey);
            if (existingToken != null && !existingToken.isBlank()) {
                QueueResponseDTO existingStatus = tryReuseExistingToken(redis, activeKey, queueKey, existingToken, now);
                if (existingStatus != null) {
                    return existingStatus;
                }
            }

            // 2. 신규 토큰 발급 및 Lua 진입 처리
            String token = UUID.randomUUID().toString();
            long expiresAt = now + (tokenTtlSeconds * 1000L);

            Long result = redis.execute(
                    ENTER_QUEUE_SCRIPT,
                    List.of(activeKey, queueKey, SCHEDULE_SET_KEY),
                    token,
                    String.valueOf(now),
                    String.valueOf(expiresAt),
                    String.valueOf(maxActiveUsers),
                    String.valueOf(scheduleSeq)
            );

            saveTokenMetadata(redis, token, memberId, scheduleSeq, now, userKey);

            if (result != null && result == 0L) {
                return QueueResponseDTO.builder()
                        .queueToken(token)
                        .queueRank(0L)
                        .isAvailable(true)
                        .message("즉시 예매 가능합니다.")
                        .build();
            }

            return QueueResponseDTO.builder()
                    .queueToken(token)
                    .queueRank(result != null ? result : 1L)
                    .isAvailable(false)
                    .message("대기 중입니다.")
                    .build();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis enterQueue failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 대기열 순번 조회 (폴링용, lastSeen 갱신 및 active ZSET 단일 기준 판별) */
    public QueueResponseDTO checkQueueRank(String queueToken) {
        if (queueToken == null || queueToken.isBlank()) {
            throw new ApiException(ErrorCode.QUEUE_TOKEN_REQUIRED);
        }

        StringRedisTemplate redis = requireRedis();
        String metaKey = META_KEY_PREFIX + queueToken;

        try {
            HashOperations<String, Object, Object> hashOps = redis.opsForHash();
            Map<Object, Object> meta = hashOps.entries(metaKey);
            if (meta == null || meta.isEmpty() || meta.get("scheduleSeq") == null) {
                throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
            }

            String scheduleSeqStr = String.valueOf(meta.get("scheduleSeq"));
            String memberId = meta.get("memberId") != null ? String.valueOf(meta.get("memberId")) : null;
            long now = System.currentTimeMillis();

            // 생존 신호(lastSeen) 갱신
            hashOps.put(metaKey, "lastSeen", String.valueOf(now));
            redis.expire(metaKey, Duration.ofMinutes(META_TTL_MINUTES));

            String activeKey = ACTIVE_KEY_PREFIX + scheduleSeqStr;
            Double activeScore = redis.opsForZSet().score(activeKey, queueToken);
            if (activeScore != null && activeScore.longValue() > now) {
                return QueueResponseDTO.builder()
                        .queueToken(queueToken)
                        .queueRank(0L)
                        .isAvailable(true)
                        .message("입장 차례입니다. 예매를 진행해 주세요.")
                        .build();
            }

            String queueKey = QUEUE_KEY_PREFIX + scheduleSeqStr;
            Long rank = redis.opsForZSet().rank(queueKey, queueToken);
            if (rank != null) {
                return QueueResponseDTO.builder()
                        .queueToken(queueToken)
                        .queueRank(rank + 1)
                        .isAvailable(false)
                        .message("대기 중입니다.")
                        .build();
            }

            // 만료되었거나 유령 토큰으로 정리된 경우 메타/유저 매핑 정리 후 예외
            redis.delete(metaKey);
            if (memberId != null) {
                redis.delete(USER_KEY_PREFIX + scheduleSeqStr + ":" + memberId);
            }
            throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis checkQueueRank failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 좌석 선점 시 대기열 입장권 원자적 검증 및 만료 시간 연장 */
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
        long now = System.currentTimeMillis();
        long extendedExpiresAt = now + (extendSeconds * 1000L);
        String activeKey = ACTIVE_KEY_PREFIX + scheduleSeq;
        String metaKey = META_KEY_PREFIX + queueToken;
        String userKey = userKey(scheduleSeq, memberId);

        try {
            Long result = redis.execute(
                    VALIDATE_AND_EXTEND_SCRIPT,
                    List.of(activeKey, metaKey, userKey),
                    queueToken,
                    memberId,
                    String.valueOf(scheduleSeq),
                    String.valueOf(now),
                    String.valueOf(extendedExpiresAt),
                    String.valueOf(Duration.ofMinutes(META_TTL_MINUTES).toSeconds())
            );

            if (result == null || result == -1L) {
                throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
            }
            if (result == -2L) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis validateAndExtendForHold failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 결제 완료·예매 취소·만료 시 대기열 슬롯 즉시 반납 */
    public void releaseSlot(Long scheduleSeq, String memberId) {
        if (redisTemplate == null || scheduleSeq == null || memberId == null || memberId.isBlank()) {
            return;
        }

        try {
            String userKey = userKey(scheduleSeq, memberId);
            String token = redisTemplate.opsForValue().get(userKey);
            if (token == null || token.isBlank()) {
                return;
            }

            redisTemplate.execute(
                    RELEASE_SLOT_SCRIPT,
                    List.of(ACTIVE_KEY_PREFIX + scheduleSeq, QUEUE_KEY_PREFIX + scheduleSeq, userKey, META_KEY_PREFIX + token),
                    token
            );
        } catch (Exception e) {
            log.warn("[QueueService] Redis releaseSlot warning: {}", e.getMessage());
        }
    }

    /** 만료된 입장권 정리 후 여유 슬롯만큼 대기열 상위 인원 입장 (3초마다) */
    @Scheduled(fixedDelay = 3000)
    public void scheduleAdmitQueue() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Set<String> scheduleIds = redisTemplate.opsForSet().members(SCHEDULE_SET_KEY);
            if (scheduleIds == null || scheduleIds.isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            long expiresAt = now + (tokenTtlSeconds * 1000L);
            long ghostCutoff = now - (ghostTimeoutSeconds * 1000L);

            for (String scheduleId : scheduleIds) {
                String queueKey = QUEUE_KEY_PREFIX + scheduleId;
                String activeKey = ACTIVE_KEY_PREFIX + scheduleId;

                redisTemplate.execute(
                        ADMIT_BATCH_SCRIPT,
                        List.of(activeKey, queueKey),
                        String.valueOf(now),
                        String.valueOf(expiresAt),
                        String.valueOf(maxActiveUsers),
                        String.valueOf(admitBatchSize),
                        String.valueOf(ghostCutoff),
                        META_KEY_PREFIX
                );

                Long remaining = redisTemplate.opsForZSet().zCard(queueKey);
                if (remaining == null || remaining == 0L) {
                    redisTemplate.opsForSet().remove(SCHEDULE_SET_KEY, scheduleId);
                }
            }
        } catch (Exception e) {
            log.warn("[QueueService] Redis scheduler error: {}", e.getMessage());
        }
    }

    private StringRedisTemplate requireRedis() {
        if (redisTemplate == null) {
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        return redisTemplate;
    }

    private QueueResponseDTO tryReuseExistingToken(
            StringRedisTemplate redis, String activeKey, String queueKey, String existingToken, long now) {
        String metaKey = META_KEY_PREFIX + existingToken;
        Double activeScore = redis.opsForZSet().score(activeKey, existingToken);
        if (activeScore != null && activeScore.longValue() > now) {
            redis.opsForHash().put(metaKey, "lastSeen", String.valueOf(now));
            redis.expire(metaKey, Duration.ofMinutes(META_TTL_MINUTES));
            return QueueResponseDTO.builder()
                    .queueToken(existingToken)
                    .queueRank(0L)
                    .isAvailable(true)
                    .message("즉시 예매 가능합니다.")
                    .build();
        }

        Long rank = redis.opsForZSet().rank(queueKey, existingToken);
        if (rank != null) {
            redis.opsForHash().put(metaKey, "lastSeen", String.valueOf(now));
            redis.expire(metaKey, Duration.ofMinutes(META_TTL_MINUTES));
            return QueueResponseDTO.builder()
                    .queueToken(existingToken)
                    .queueRank(rank + 1)
                    .isAvailable(false)
                    .message("대기 중입니다.")
                    .build();
        }

        return null;
    }

    private void saveTokenMetadata(
            StringRedisTemplate redis, String token, String memberId, Long scheduleSeq, long now, String userKey) {
        String metaKey = META_KEY_PREFIX + token;
        Duration ttl = Duration.ofMinutes(META_TTL_MINUTES);
        redis.opsForHash().putAll(metaKey, Map.of(
                "memberId", memberId,
                "scheduleSeq", String.valueOf(scheduleSeq),
                "lastSeen", String.valueOf(now)
        ));
        redis.expire(metaKey, ttl);
        redis.opsForValue().set(userKey, token, ttl);
    }

    private String userKey(Long scheduleSeq, String memberId) {
        return USER_KEY_PREFIX + scheduleSeq + ":" + memberId;
    }
}
