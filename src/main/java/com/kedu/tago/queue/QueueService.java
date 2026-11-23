package com.kedu.tago.queue;

import com.kedu.tago.queue.dto.QueueResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Slf4j
@Service
public class QueueService {

    private final StringRedisTemplate redisTemplate;

    // Redis 장애/미구동 시 안전하게 동작하기 위한 In-Memory Fallback
    // token -> enterTime
    private final Map<String, Long> inMemoryQueue = new ConcurrentSkipListMap<>();
    private final Set<String> inMemoryAdmittedTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final String QUEUE_KEY_PREFIX = "tago:queue:";
    private static final String QUEUE_SEQ_KEY_PREFIX = "tago:queue:seq:";
    private static final String ADMITTED_SET_KEY = "tago:queue:admitted";
    private static final long ALLOWED_BURST_COUNT = 10; // 회차당 한 번에 입장 허용할 인원

    public QueueService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 대기열 진입 (R001: Redis INCR 단조 증가 시퀀스를 score로 사용, ZADD NX 로 기존 점수 보호)
     */
    public QueueResponseDTO enterQueue(Long scheduleSeq, String memberId) {
        String token = UUID.randomUUID().toString();

        if (redisTemplate != null) {
            try {
                String queueKey = QUEUE_KEY_PREFIX + scheduleSeq;
                String seqKey = QUEUE_SEQ_KEY_PREFIX + scheduleSeq;

                // Redis INCR로 동일 밀리초 내 동시 유입 시에도 중복 없는 도착 순번 발급
                Long sequence = redisTemplate.opsForValue().increment(seqKey);
                if (sequence == null) {
                    throw new IllegalStateException("Failed to issue sequence from Redis INCR: " + seqKey);
                }

                // ZADD NX
                redisTemplate.opsForZSet().addIfAbsent(queueKey, token, sequence.doubleValue());
                Long rank = redisTemplate.opsForZSet().rank(queueKey, token);

                // 이미 통과 상태인지 확인
                Boolean isAdmitted = redisTemplate.opsForSet().isMember(ADMITTED_SET_KEY, token);

                if (Boolean.TRUE.equals(isAdmitted) || (rank != null && rank < ALLOWED_BURST_COUNT)) {
                    redisTemplate.opsForSet().add(ADMITTED_SET_KEY, token);
                    return QueueResponseDTO.builder()
                            .queueToken(token)
                            .queueRank(0L)
                            .isAvailable(true)
                            .message("즉시 예매 가능합니다.")
                            .build();
                }

                return QueueResponseDTO.builder()
                        .queueToken(token)
                        .queueRank(rank != null ? rank + 1 : 1L)
                        .isAvailable(false)
                        .message("대기 중입니다.")
                        .build();
            } catch (Exception e) {
                log.warn("[QueueService] Redis unavailable, fallback to in-memory: {}", e.getMessage());
            }
        }

        // In-Memory Fallback
        inMemoryQueue.put(token, System.currentTimeMillis());
        int currentSize = inMemoryQueue.size();
        if (currentSize <= ALLOWED_BURST_COUNT) {
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

    /**
     * 대기열 순번 조회 (5초 간격 폴링용)
     */
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

                // 전체 queue 키들을 뒤지지 않고, 통과되지 않은 경우 첫 번째 랭크 추정 또는 1 이상 반환
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

        // In-Memory Fallback
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

    /**
     * 주기적으로 대기열 상위 인원 입장 허용 (스케줄러: 3초마다 상위 N명 통과)
     */
    @Scheduled(fixedDelay = 3000)
    public void scheduleAdmitQueue() {
        if (!inMemoryQueue.isEmpty()) {
            Iterator<String> iterator = inMemoryQueue.keySet().iterator();
            int admitted = 0;
            while (iterator.hasNext() && admitted < ALLOWED_BURST_COUNT) {
                String token = iterator.next();
                inMemoryAdmittedTokens.add(token);
                iterator.remove();
                admitted++;
            }
        }
    }
}
