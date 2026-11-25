package com.kedu.tago.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 대기열 → 입장 전환 (1초마다, 운행별로 빈 자리만큼).
 * 서버가 여러 대여도 한 번에 한 대만 입장 처리하도록 Redisson 락을 건다 (못 잡으면 이번 회차는 건너뜀).
 * tago.queue.scheduler-enabled=false 이면 등록하지 않는다 (통합 테스트는 입장 처리를 직접 호출한다).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "tago.queue.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class QueueScheduler {

    private static final String LOCK_KEY = "lock:queue:scheduler";
    private static final long LOCK_LEASE_SECONDS = 10;

    private final QueueAdmissionService admissionService;
    private final RedissonClient redisson;

    @Scheduled(fixedDelay = 1000)
    public void moveWaitingToActive() {
        RLock lock = redisson.getLock(LOCK_KEY);
        boolean locked;
        try {
            locked = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.warn("[QueueScheduler] 락 획득 실패: {}", e.getMessage());
            return;
        }
        if (!locked) {
            return;
        }
        try {
            for (String scheduleSeq : admissionService.getWaitingSchedules()) {
                try {
                    int admitted = admissionService.admitWaiting(scheduleSeq);
                    if (admitted > 0) {
                        log.info("[QueueScheduler] 운행 {}: 대기열 -> 입장 {}명", scheduleSeq, admitted);
                    }
                } catch (Exception e) {
                    log.warn("[QueueScheduler] 운행 {} 입장 처리 실패: {}", scheduleSeq, e.getMessage());
                }
            }
        } finally {
            try {
                lock.unlock();
            } catch (IllegalMonitorStateException e) {
                log.warn("[QueueScheduler] 락이 이미 풀림: {}", e.getMessage());
            }
        }
    }
}
