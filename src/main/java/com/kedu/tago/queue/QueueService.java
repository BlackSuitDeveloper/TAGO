package com.kedu.tago.queue;

import com.kedu.tago.queue.dto.QueueResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/** Redis 기반 대기열 서비스 (Redis 장애 시 인메모리 대체) */
@Slf4j
@Service
public class QueueService {

    private final StringRedisTemplate redisTemplate;

    // Redis 장애/미구동 시 사용하는 인메모리 대체 저장소
    private final Map<String, Long> inMemoryQueue = new ConcurrentSkipListMap<>();
    private final Set<String> inMemoryAdmittedTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final String QUEUE_KEY_PREFIX = "tago:queue:";
    private static final String ACTIVE_KEY_PREFIX = "tago:queue:active:";
    private static final String META_KEY_PREFIX = "tago:queue:meta:";
    private static final String ADMITTED_SET_KEY = "tago:queue:admitted";
    private static final String SCHEDULE_SET_KEY = "tago:queue:schedules";

    // 대기열 진입 Lua 스크립트
    // KEYS: activeKey, queueKey, admittedSetKey, scheduleSetKey
    // ARGV: token, now, expiresAt, maxActiveUsers, scheduleSeq
    // 반환: 0이면 즉시 입장, 1 이상이면 대기 순번(1부터)
    private static final DefaultRedisScript<Long> ENTER_QUEUE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[2])
            local activeCount = redis.call('ZCARD', KEYS[1])
            if activeCount < tonumber(ARGV[4]) then
                redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
                redis.call('SADD', KEYS[3], ARGV[1])
                return 0
            else
                redis.call('ZADD', KEYS[2], 'NX', ARGV[2], ARGV[1])
                redis.call('SADD', KEYS[4], ARGV[5])
                local rank = redis.call('ZRANK', KEYS[2], ARGV[1])
                return (rank or 0) + 1
            end
            """, Long.class);

    // 대기열 순차 입장 Lua 스크립트
    // KEYS: activeKey, queueKey, admittedSetKey
    // ARGV: now, expiresAt, maxActiveUsers, admitBatchSize
    private static final DefaultRedisScript<Long> ADMIT_BATCH_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local activeCount = redis.call('ZCARD', KEYS[1])
            local slots = tonumber(ARGV[3]) - activeCount
            if slots <= 0 then
                return 0
            end
            local admitCount = math.min(slots, tonumber(ARGV[4]))
            if admitCount <= 0 then
                return 0
            end
            local tokens = redis.call('ZRANGE', KEYS[2], 0, admitCount - 1)
            for _, token in ipairs(tokens) do
                redis.call('ZREM', KEYS[2], token)
                redis.call('ZADD', KEYS[1], ARGV[2], token)
                redis.call('SADD', KEYS[3], token)
            end
            return #tokens
            """, Long.class);

    // 단일 서버 기준 제어치: 열차당 동시 입장 200명, 3초마다 30명 입장, 입장권 TTL 5분
    @Value("${tago.queue.max-active:200}")
    private long maxActiveUsers = 200;

    @Value("${tago.queue.batch-size:30}")
    private long admitBatchSize = 30;

    @Value("${tago.queue.token-ttl-seconds:300}")
    private long tokenTtlSeconds = 300;

    public QueueService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 대기열 진입 (동시 입장 한도 미만이면 즉시 입장, 초과 시 대기 순번 반환) */
    public QueueResponseDTO enterQueue(Long scheduleSeq, String memberId) {
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        long expiresAt = now + (tokenTtlSeconds * 1000L);

        if (redisTemplate != null && scheduleSeq != null) {
            try {
                String queueKey = QUEUE_KEY_PREFIX + scheduleSeq;
                String activeKey = ACTIVE_KEY_PREFIX + scheduleSeq;

                Long result = redisTemplate.execute(
                        ENTER_QUEUE_SCRIPT,
                        List.of(activeKey, queueKey, ADMITTED_SET_KEY, SCHEDULE_SET_KEY),
                        token,
                        String.valueOf(now),
                        String.valueOf(expiresAt),
                        String.valueOf(maxActiveUsers),
                        String.valueOf(scheduleSeq)
                );

                if (result != null && result == 0L) {
                    return QueueResponseDTO.builder()
                            .queueToken(token)
                            .queueRank(0L)
                            .isAvailable(true)
                            .message("즉시 예매 가능합니다.")
                            .build();
                }

                // 폴링 시 순번 조회를 위해 토큰-스케줄 매핑 저장
                redisTemplate.opsForValue().set(META_KEY_PREFIX + token, String.valueOf(scheduleSeq), Duration.ofMinutes(30));

                return QueueResponseDTO.builder()
                        .queueToken(token)
                        .queueRank(result != null ? result : 1L)
                        .isAvailable(false)
                        .message("대기 중입니다.")
                        .build();
            } catch (Exception e) {
                log.warn("[QueueService] Redis unavailable, fallback to in-memory: {}", e.getMessage());
            }
        }

        // 인메모리 대체 처리
        inMemoryQueue.put(token, now);
        int currentSize = inMemoryQueue.size();
        if (currentSize <= maxActiveUsers) {
            inMemoryAdmittedTokens.add(token);
            return QueueResponseDTO.builder()
                    .queueToken(token)
                    .queueRank(0L)
                    .isAvailable(true)
                    .message("즉시 예매 가능합니다.")
                    .build();
        }

        return QueueResponseDTO.builder()
                .queueToken(token)
                .queueRank((long) currentSize)
                .isAvailable(false)
                .message("대기 중입니다.")
                .build();
    }

    /** 대기열 순번 조회 (폴링용) */
    public QueueResponseDTO checkQueueRank(String queueToken) {
        if (redisTemplate != null) {
            try {
                Boolean isAdmitted = redisTemplate.opsForSet().isMember(ADMITTED_SET_KEY, queueToken);
                if (Boolean.TRUE.equals(isAdmitted)) {
                    return QueueResponseDTO.builder()
                            .queueToken(queueToken)
                            .queueRank(0L)
                            .isAvailable(true)
                            .message("입장 차례입니다. 예매를 진행해 주세요.")
                            .build();
                }

                String scheduleSeqStr = redisTemplate.opsForValue().get(META_KEY_PREFIX + queueToken);
                if (scheduleSeqStr != null) {
                    Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY_PREFIX + scheduleSeqStr, queueToken);
                    if (rank != null) {
                        return QueueResponseDTO.builder()
                                .queueToken(queueToken)
                                .queueRank(rank + 1)
                                .isAvailable(false)
                                .message("대기 중입니다.")
                                .build();
                    }
                }

                return QueueResponseDTO.builder()
                        .queueToken(queueToken)
                        .queueRank(1L)
                        .isAvailable(false)
                        .message("대기 중입니다.")
                        .build();
            } catch (Exception e) {
                log.warn("[QueueService] Redis check error: {}", e.getMessage());
            }
        }

        // 인메모리 대체 처리
        if (inMemoryAdmittedTokens.contains(queueToken)) {
            return QueueResponseDTO.builder()
                    .queueToken(queueToken)
                    .queueRank(0L)
                    .isAvailable(true)
                    .message("입장 차례입니다. 예매를 진행해 주세요.")
                    .build();
        }

        return QueueResponseDTO.builder()
                .queueToken(queueToken)
                .queueRank(1L)
                .isAvailable(false)
                .message("대기 중입니다.")
                .build();
    }

    /** 만료된 입장권 정리 후 여유 슬롯만큼 대기열 상위 인원 입장 (3초마다) */
    @Scheduled(fixedDelay = 3000)
    public void scheduleAdmitQueue() {
        if (redisTemplate != null) {
            try {
                Set<String> scheduleIds = redisTemplate.opsForSet().members(SCHEDULE_SET_KEY);
                if (scheduleIds != null && !scheduleIds.isEmpty()) {
                    long now = System.currentTimeMillis();
                    long expiresAt = now + (tokenTtlSeconds * 1000L);

                    for (String scheduleId : scheduleIds) {
                        String queueKey = QUEUE_KEY_PREFIX + scheduleId;
                        String activeKey = ACTIVE_KEY_PREFIX + scheduleId;

                        Long admitted = redisTemplate.execute(
                                ADMIT_BATCH_SCRIPT,
                                List.of(activeKey, queueKey, ADMITTED_SET_KEY),
                                String.valueOf(now),
                                String.valueOf(expiresAt),
                                String.valueOf(maxActiveUsers),
                                String.valueOf(admitBatchSize)
                        );

                        if (admitted == null || admitted == 0L) {
                            Long remaining = redisTemplate.opsForZSet().zCard(queueKey);
                            if (remaining == null || remaining == 0) {
                                redisTemplate.opsForSet().remove(SCHEDULE_SET_KEY, scheduleId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[QueueService] Redis scheduler error: {}", e.getMessage());
            }
        }

        if (!inMemoryQueue.isEmpty()) {
            Iterator<String> iterator = inMemoryQueue.keySet().iterator();
            int admitted = 0;
            while (iterator.hasNext() && admitted < admitBatchSize) {
                String token = iterator.next();
                inMemoryAdmittedTokens.add(token);
                iterator.remove();
                admitted++;
            }
        }
    }
}
