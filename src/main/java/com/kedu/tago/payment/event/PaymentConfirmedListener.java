package com.kedu.tago.payment.event;

import com.kedu.tago.queue.QueuePassService;
import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;
import com.kedu.tago.seat.hold.SeatHoldService;
import com.kedu.tago.seat.SeatEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DB 커밋이 끝난 뒤에만 Redis 정리·슬롯 반납·SSE 알림 (롤백되면 실행되지 않음).
 *
 * 여기서 예외가 나면 결제 API 가 500 으로 응답한다 (DB 는 이미 커밋됐는데도).
 * 그래서 모든 단계를 try/catch 로 감싸고, Redis 정리가 실패하면 TTL 을 1초로 줄여 즉시 반납한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmedListener {

    private final SeatHoldService seatHoldService;
    private final QueuePassService queuePassService;
    private final SeatEventPublisher seatEventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(PaymentConfirmedEvent event) {
        try {
            seatHoldService.clearAfterConfirm(event.scheduleSeq(), event.memberId(), event.seats());
        } catch (Exception e) {
            log.error("[After Commit] 선점 키 정리 실패, TTL 즉시 만료로 대체: {}", e.getMessage());
            try {
                seatHoldService.expireSoon(event.scheduleSeq(), event.memberId(), event.seats());
            } catch (Exception e2) {
                // Redis 자체가 죽은 경우: 원래 TTL 이 지나면 자동으로 사라진다
                log.error("[After Commit] TTL 단축도 실패, 원래 TTL 로 만료 예정: {}", e2.getMessage());
            }
        }
        queuePassService.releaseSlot(event.scheduleSeq(), event.memberId()); // 내부에서 예외를 삼킨다
        try {
            for (SeatSelection s : event.seats()) {
                seatEventPublisher.publish(event.scheduleSeq(), s.getCarNo(), s.getSeatNo(), "CONFIRMED");
            }
        } catch (Exception e) {
            log.warn("[After Commit] SSE 알림 실패: {}", e.getMessage());
        }
    }
}
