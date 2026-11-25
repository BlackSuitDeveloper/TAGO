package com.kedu.tago.queue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 대기열 → 입장 전환 (QueueScheduler 가 1초마다 호출).
 *
 * 운행별로 빈 자리(max-active - 현재 입장 인원)만큼, 한 번에 batch-size 명까지 대기열 앞에서부터 입장시킨다.
 * 입장 처리는 이 클래스 한 곳에서만 하고 스케줄러는 Redisson 락으로 한 대만 돌기 때문에,
 * 원자 스크립트 없이도 동시 입장 한도를 넘지 않는다.
 *
 * 순서: 입장 키 생성 → 대기열에서 제거 (조회 쪽은 반대로 대기 순번 → 입장 키 순서로 본다).
 */
@Service
public class QueueAdmissionService {

    private final StringRedisTemplate redisTemplate;

    @Value("${tago.queue.max-active:200}")
    private long maxActiveUsers = 200;

    @Value("${tago.queue.batch-size:30}")
    private long admitBatchSize = 30;

    @Value("${tago.queue.token-ttl-seconds:300}")
    private long tokenTtlSeconds = 300;

    @Value("${tago.queue.ghost-timeout-seconds:120}")
    private long ghostTimeoutSeconds = 120;

    public QueueAdmissionService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 대기자가 있는 운행 목록 */
    public Set<String> getWaitingSchedules() {
        if (redisTemplate == null) {
            return Set.of();
        }
        Set<String> ids = redisTemplate.opsForSet().members(QueueKeys.SCHEDULES);
        return ids != null ? ids : Set.of();
    }

    /** 현재 대기 인원 */
    public long getWaitingCount(String scheduleSeq) {
        Long size = redisTemplate.opsForZSet().zCard(QueueKeys.waiting(scheduleSeq));
        return size != null ? size : 0L;
    }

    /** 현재 입장 인원 (토큰별 키 개수, SCAN 으로 블로킹 방지) */
    public long getActiveCount(String scheduleSeq) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(QueueKeys.activePattern(scheduleSeq))
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
     * 빈 자리만큼 대기열 앞에서부터 입장. 반환: 이번에 입장한 인원.
     * 마지막 폴링이 ghost-timeout 보다 오래된 토큰(창을 닫은 사용자)은 입장시키지 않고 정리한다.
     */
    public int admitWaiting(String scheduleSeq) {
        String queueKey = QueueKeys.waiting(scheduleSeq);
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
                if (admit(scheduleSeq, token, ghostCutoff)) {
                    admitted++;
                }
                done.add(token);
            }
            redisTemplate.opsForZSet().remove(queueKey, done.toArray());
        }

        dropScheduleIfEmpty(scheduleSeq);
        return admitted;
    }

    /** 토큰 하나 입장 (유령이면 정리만). 입장했으면 true */
    private boolean admit(String scheduleSeq, String token, long ghostCutoff) {
        String metaKey = QueueKeys.meta(token);
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);
        Object memberId = meta.get("memberId");

        if (memberId == null || QueueTokens.lastSeen(meta) < ghostCutoff) {
            // 유령 토큰: 메타·회원 매핑 정리
            redisTemplate.delete(metaKey);
            if (memberId != null) {
                redisTemplate.delete(QueueKeys.user(scheduleSeq, String.valueOf(memberId)));
            }
            return false;
        }

        // 입장 키를 먼저 만든 뒤 대기열에서 뺀다 (어느 순간에도 둘 중 하나에는 있도록)
        String activeKey = QueueKeys.active(scheduleSeq, token);
        redisTemplate.opsForValue().set(activeKey, String.valueOf(memberId), Duration.ofSeconds(tokenTtlSeconds));
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(metaKey))) {
            redisTemplate.delete(activeKey); // 그 사이 슬롯을 반납(이탈)한 토큰
            return false;
        }
        return true;
    }

    /** 대기자가 없으면 운행 목록에서 뺀다. 빼는 사이 새 대기자가 들어왔으면 되돌린다 */
    private void dropScheduleIfEmpty(String scheduleSeq) {
        String queueKey = QueueKeys.waiting(scheduleSeq);
        Long remaining = redisTemplate.opsForZSet().zCard(queueKey);
        if (remaining != null && remaining > 0L) {
            return;
        }
        redisTemplate.opsForSet().remove(QueueKeys.SCHEDULES, scheduleSeq);
        Long again = redisTemplate.opsForZSet().zCard(queueKey);
        if (again != null && again > 0L) {
            redisTemplate.opsForSet().add(QueueKeys.SCHEDULES, scheduleSeq);
        }
    }
}
