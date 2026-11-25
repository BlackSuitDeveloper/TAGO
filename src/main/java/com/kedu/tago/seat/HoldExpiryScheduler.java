package com.kedu.tago.seat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis TTL 로 풀린 선점 좌석을 SSE 로 알림 (1초마다).
 * 알리기 전에 DB 를 확인한다: 결제 확정 후 정리가 실패해 TTL 로 사라진 키라면 AVAILABLE 이 아니라 CONFIRMED 로 알린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HoldExpiryScheduler {

    private final SeatHoldService seatHoldService;
    private final SeatService seatService;
    private final SeatMapper seatMapper;

    @Scheduled(fixedDelay = 1000)
    public void broadcastExpiredHolds() {
        try {
            List<String[]> expired = seatHoldService.pollExpiredHolds();
            for (String[] p : expired) {
                Long scheduleSeq = Long.valueOf(p[0]);
                Integer carNo = Integer.valueOf(p[1]);
                boolean sold = seatMapper.selectSeat(scheduleSeq, carNo, p[2]) != null;
                seatService.broadcastSeatStatusChange(scheduleSeq, carNo, p[2], sold ? "CONFIRMED" : "AVAILABLE");
            }
            if (!expired.isEmpty()) {
                log.info("[Hold Expiry] 만료 선점 {}석 해제 알림", expired.size());
            }
        } catch (Exception e) {
            log.warn("[Hold Expiry] error: {}", e.getMessage());
        }
    }
}
