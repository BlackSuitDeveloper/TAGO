package com.kedu.tago.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 대기열 → 입장 전환 (1초마다, 운행별로 빈 자리만큼) */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final QueueService queueService;

    @Scheduled(fixedDelay = 1000)
    public void moveWaitingToActive() {
        for (String scheduleSeq : queueService.getWaitingSchedules()) {
            try {
                int admitted = queueService.admitWaiting(scheduleSeq);
                if (admitted > 0) {
                    log.info("[QueueScheduler] 운행 {}: 대기열 -> 입장 {}명", scheduleSeq, admitted);
                }
            } catch (Exception e) {
                log.warn("[QueueScheduler] 운행 {} 입장 처리 실패: {}", scheduleSeq, e.getMessage());
            }
        }
    }
}
