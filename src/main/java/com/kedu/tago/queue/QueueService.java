package com.kedu.tago.queue;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redis 기반 대기열 서비스 (Fail-closed 정책, 회원+열차별 멱등 토큰, 유령 토큰 정리).
 *
 * tago:queue:wait:{s}           ZSet(token, 진입 시각)  대기열
 * tago:queue:active:{s}:{token} = memberId (TTL)        입장 허용 (TTL 이 지나면 자동 퇴장)
 * tago:queue:meta:{token}       Hash(memberId, scheduleSeq, lastSeen)
 * tago:queue:user:{s}:{member}  = token                 회원+열차별 토큰 (멱등 진입)
 * tago:queue:schedules          Set(scheduleSeq)        대기자가 있는 운행 목록
 *
 * 입장은 QueueScheduler 가 1초마다 처리한다 (빈 자리만큼 대기열 앞에서부터).
 * 입장 처리를 스케줄러 한 곳에서만 하므로 Lua 스크립트 없이도 동시 입장 한도를 넘지 않는다.
 */
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

    /** 대기열 진입 (회원+열차별 멱등 진입). 입장은 스케줄러가 처리한다 */
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
        String userKey = userKey(scheduleSeq, memberId);

        try {
            // 1. 기존 유효 토큰이 있으면 멱등 반환 (중복 진입 방지)
            String existingToken = redis.opsForValue().get(userKey);
            if (existingToken != null && !existingToken.isBlank()) {
                QueueResponseDTO existingStatus = tryReuseExistingToken(redis, scheduleSeq, existingToken, now);
                if (existingStatus != null) {
                    return existingStatus;
                }
            }

            // 2. 신규 토큰 발급: 메타를 먼저 저장(스케줄러 유령 오판 방지)한 뒤 대기열에 넣는다
            String token = UUID.randomUUID().toString();
            saveTokenMetadata(redis, token, memberId, scheduleSeq, now, userKey);
            redis.opsForZSet().addIfAbsent(queueKey, token, now);
            redis.opsForSet().add(SCHEDULE_SET_KEY, String.valueOf(scheduleSeq));

            Long rank = redis.opsForZSet().rank(queueKey, token);
            return waiting(token, rank);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis enterQueue failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 대기열 순번 조회 (폴링용, lastSeen 갱신) */
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

            // 생존 신호(lastSeen) 갱신
            hashOps.put(metaKey, "lastSeen", String.valueOf(System.currentTimeMillis()));
            redis.expire(metaKey, Duration.ofMinutes(META_TTL_MINUTES));

            if (Boolean.TRUE.equals(redis.hasKey(activeKey(scheduleSeqStr, queueToken)))) {
                return admitted(queueToken, "입장 차례입니다. 예매를 진행해 주세요.");
            }

            Long rank = redis.opsForZSet().rank(QUEUE_KEY_PREFIX + scheduleSeqStr, queueToken);
            if (rank != null) {
                return waiting(queueToken, rank);
            }

            // 입장 시간이 끝났거나 유령 토큰으로 정리된 경우 메타/유저 매핑 정리 후 예외
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
        String metaKey = META_KEY_PREFIX + queueToken;
        String activeKey = activeKey(String.valueOf(scheduleSeq), queueToken);

        try {
            // 1. 토큰 주인·열차 확인
            Map<Object, Object> meta = redis.opsForHash().entries(metaKey);
            if (meta == null || !memberId.equals(meta.get("memberId"))
                    || !String.valueOf(scheduleSeq).equals(meta.get("scheduleSeq"))) {
                throw new ApiException(ErrorCode.QUEUE_TOKEN_INVALID);
            }

            // 2. 입장 상태 확인 + 남은 시간이 선점 시간보다 짧으면 연장
            Long remaining = redis.getExpire(activeKey);
            if (remaining == null || remaining == -2L) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED);
            }
            if (remaining >= 0 && remaining < extendSeconds
                    && !Boolean.TRUE.equals(redis.expire(activeKey, Duration.ofSeconds(extendSeconds)))) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED); // 확인과 연장 사이에 만료됨
            }

            // 3. 생존 신호 갱신
            redis.opsForHash().put(metaKey, "lastSeen", String.valueOf(System.currentTimeMillis()));
            redis.expire(metaKey, Duration.ofMinutes(META_TTL_MINUTES));
            redis.expire(userKey(scheduleSeq, memberId), Duration.ofMinutes(META_TTL_MINUTES));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis validateAndExtendForHold failure: {}", e.getMessage());
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
            if (!Boolean.TRUE.equals(redis.hasKey(activeKey(String.valueOf(scheduleSeq), queueToken)))) {
                throw new ApiException(ErrorCode.QUEUE_NOT_ADMITTED);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[QueueService] Redis validateActive failure: {}", e.getMessage());
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
    }

    /** 결제 완료·예매 취소·이탈 시 대기열 슬롯 즉시 반납 */
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
            redisTemplate.delete(activeKey(String.valueOf(scheduleSeq), token));
            redisTemplate.opsForZSet().remove(QUEUE_KEY_PREFIX + scheduleSeq, token);
            redisTemplate.delete(List.of(userKey, META_KEY_PREFIX + token));
        } catch (Exception e) {
            log.warn("[QueueService] Redis releaseSlot warning: {}", e.getMessage());
        }
    }

    /** 대기자가 있는 운행 목록 (스케줄러용) */
    public Set<String> getWaitingSchedules() {
        if (redisTemplate == null) {
            return Set.of();
        }
        Set<String> ids = redisTemplate.opsForSet().members(SCHEDULE_SET_KEY);
        return ids != null ? ids : Set.of();
    }

    /** 현재 입장 인원 (개별 키 카운트, SCAN 으로 블로킹 방지) */
    public long getActiveCount(String scheduleSeq) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(ACTIVE_KEY_PREFIX + scheduleSeq + ":*")
                .count(100)
                .build();
        long count = 0;
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        return count;
    }

    /**
     * 빈 자리만큼 대기열 앞에서부터 입장 (QueueScheduler 가 1초마다 호출).
     * 마지막 폴링이 ghostTimeoutSeconds 보다 오래된 토큰(창을 닫은 사용자)은 입장시키지 않고 정리한다.
     * 반환: 이번에 입장한 인원
     */
    public int admitWaiting(String scheduleSeq) {
        String queueKey = QUEUE_KEY_PREFIX + scheduleSeq;
        long slots = Math.min(maxActiveUsers - getActiveCount(scheduleSeq), admitBatchSize);
        long ghostCutoff = System.currentTimeMillis() - ghostTimeoutSeconds * 1000L;
        int admitted = 0;

        while (admitted < slots) {
            Set<String> head = redisTemplate.opsForZSet().range(queueKey, 0, slots - admitted - 1);
            if (head == null || head.isEmpty()) {
                break;
            }
            List<String> done = new ArrayList<>();
            for (String token : head) {
                Map<Object, Object> meta = redisTemplate.opsForHash().entries(META_KEY_PREFIX + token);
                Object memberId = meta.get("memberId");
                Object lastSeen = meta.get("lastSeen");
                if (memberId != null && lastSeen != null && Long.parseLong(String.valueOf(lastSeen)) >= ghostCutoff) {
                    // 입장 키를 먼저 만든 뒤 대기열에서 뺀다 (어느 순간에도 둘 중 하나에는 있도록)
                    redisTemplate.opsForValue().set(activeKey(scheduleSeq, token), String.valueOf(memberId),
                            Duration.ofSeconds(tokenTtlSeconds));
                    admitted++;
                } else {
                    // 유령 토큰: 메타·회원 매핑 정리
                    redisTemplate.delete(META_KEY_PREFIX + token);
                    if (memberId != null) {
                        redisTemplate.delete(USER_KEY_PREFIX + scheduleSeq + ":" + memberId);
                    }
                }
                done.add(token);
            }
            redisTemplate.opsForZSet().remove(queueKey, done.toArray());
        }

        Long remaining = redisTemplate.opsForZSet().zCard(queueKey);
        if (remaining == null || remaining == 0L) {
            redisTemplate.opsForSet().remove(SCHEDULE_SET_KEY, scheduleSeq);
        }
        return admitted;
    }

    private StringRedisTemplate requireRedis() {
        if (redisTemplate == null) {
            throw new ApiException(ErrorCode.QUEUE_SERVICE_UNAVAILABLE);
        }
        return redisTemplate;
    }

    private QueueResponseDTO tryReuseExistingToken(StringRedisTemplate redis, Long scheduleSeq, String existingToken, long now) {
        String metaKey = META_KEY_PREFIX + existingToken;
        boolean active = Boolean.TRUE.equals(redis.hasKey(activeKey(String.valueOf(scheduleSeq), existingToken)));
        Long rank = active ? null : redis.opsForZSet().rank(QUEUE_KEY_PREFIX + scheduleSeq, existingToken);
        if (!active && rank == null) {
            return null;
        }

        redis.opsForHash().put(metaKey, "lastSeen", String.valueOf(now));
        redis.expire(metaKey, Duration.ofMinutes(META_TTL_MINUTES));
        return active ? admitted(existingToken, "즉시 예매 가능합니다.") : waiting(existingToken, rank);
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

    public static String activeKey(String scheduleSeq, String token) {
        return ACTIVE_KEY_PREFIX + scheduleSeq + ":" + token;
    }

    private String userKey(Long scheduleSeq, String memberId) {
        return USER_KEY_PREFIX + scheduleSeq + ":" + memberId;
    }
}
