package com.kedu.tago.payment;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.dto.PaymentDTO;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.payment.event.PaymentConfirmedEvent;
import com.kedu.tago.queue.QueuePassService;
import com.kedu.tago.reservation.ReservationMapper;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.reservation.dto.ReservationDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;
import com.kedu.tago.schedule.dto.ScheduleResponseDTO;
import com.kedu.tago.seat.hold.SeatHoldService;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatEventPublisher;
import com.kedu.tago.seat.SeatTemplateGenerator;
import com.kedu.tago.seat.dto.SeatDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 결제 및 발권 처리 서비스 (Redis 선점 방식)
 *
 * 0. 멱등키 확인 (같은 키 재요청이면 저장된 결과 반환)
 * 1. Redis 선점 확인 + TTL 연장 (락 안, 몇 ms)
 * 2. 모의 PG 승인 (락 밖)
 * 3. DB 트랜잭션: seat 행 INSERT(UNIQUE 최종 방어선) + reservation CONFIRMED + payment
 * 4. 커밋 후: Redis 선점 키 삭제, 대기열 슬롯 반납, SSE (PaymentConfirmedListener)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationService reservationService;
    private final SeatHoldService seatHoldService;
    private final SeatMapper seatMapper;
    private final SeatEventPublisher seatEventPublisher;
    private final SeatTemplateGenerator seatTemplateGenerator;
    private final QueuePassService queuePassService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentIdempotencyService idempotencyService;

    @Value("${tago.hold.payment-extend-seconds:180}")
    private long paymentExtendSeconds;

    /**
     * 결제. 같은 Idempotency-Key 로 다시 오면 처음 결과를 그대로 돌려준다 (중복 결제 방지).
     * scheduleSeqs 로 왕복(가는 편 + 오는 편)을 한 트랜잭션에 확정할 수 있다.
     */
    public PaymentResponseDTO processPayment(PaymentRequestDTO request, String memberId, String idempotencyKey) {
        if (memberId == null) {
            throw new ApiException(ErrorCode.LOGIN_REQUIRED);
        }
        List<Long> schedules = targetSchedules(request);

        PaymentResponseDTO replay = idempotencyService.begin(memberId, idempotencyKey);
        if (replay != null) {
            log.info("[Payment Idempotent] 같은 키 재요청: 회원={}, 티켓번호={}", memberId, replay.getTicketNumber());
            return replay;
        }

        PaymentResponseDTO response;
        try {
            response = pay(request, memberId, schedules);
        } catch (RuntimeException e) {
            idempotencyService.abort(memberId, idempotencyKey); // 실패한 요청은 같은 키로 다시 시도 가능
            throw e;
        }
        try {
            idempotencyService.complete(memberId, idempotencyKey, response);
        } catch (RuntimeException e) {
            // 결제는 이미 DB 에 확정됨. 결과 저장만 실패했으므로 응답은 정상으로 돌려준다.
            log.error("[Payment Idempotent] 결과 저장 실패 (결제는 완료): {}", e.getMessage());
        }
        return response;
    }

    private List<Long> targetSchedules(PaymentRequestDTO request) {
        Set<Long> set = new LinkedHashSet<>();
        if (request.getScheduleSeq() != null) {
            set.add(request.getScheduleSeq());
        }
        if (request.getScheduleSeqs() != null) {
            request.getScheduleSeqs().stream().filter(Objects::nonNull).forEach(set::add);
        }
        if (set.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "결제할 운행 일정(scheduleSeq)이 필요합니다.");
        }
        return new ArrayList<>(set);
    }

    private PaymentResponseDTO pay(PaymentRequestDTO request, String memberId, List<Long> schedules) {
        // 1. 운행별 내 선점 좌석 확인 + 결제 처리 중 만료되지 않게 연장
        Map<Long, List<SeatSelection>> holds = new LinkedHashMap<>();
        for (Long scheduleSeq : schedules) {
            List<SeatSelection> seats = seatHoldService.getMyHolds(scheduleSeq, memberId);
            if (seats.isEmpty()) {
                throw new ApiException(ErrorCode.HOLD_EXPIRED);
            }
            holds.put(scheduleSeq, seats);
        }
        holds.forEach((scheduleSeq, seats) -> seatHoldService.verifyAndExtend(scheduleSeq, memberId, seats, paymentExtendSeconds));

        // 2. 금액 계산 (운행별 단가 × 좌석 수) 및 위변조 검증
        Map<Long, Integer> unitPrices = new HashMap<>();
        int totalPrice = 0;
        for (Map.Entry<Long, List<SeatSelection>> e : holds.entrySet()) {
            ScheduleResponseDTO schedule = reservationService.validateBookableSchedule(e.getKey());
            int unit = ReservationService.unitPrice(schedule);
            unitPrices.put(e.getKey(), unit);
            totalPrice += unit * e.getValue().size();
        }
        if (request.getTotalPrice() != null && !request.getTotalPrice().equals(totalPrice)) {
            log.warn("[Payment Error] 결제 금액 불일치! 요청금액: {}, 실제금액: {}", request.getTotalPrice(), totalPrice);
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }

        // 3. 모의 PG 승인 (락·트랜잭션 밖)
        if ("FAIL".equalsIgnoreCase(request.getMockResult())) {
            holds.forEach((scheduleSeq, seats) -> {
                for (SeatSelection s : seatHoldService.release(scheduleSeq, memberId, seats)) {
                    seatEventPublisher.publish(scheduleSeq, s.getCarNo(), s.getSeatNo(), "AVAILABLE");
                }
                queuePassService.releaseSlot(scheduleSeq, memberId);
            });
            throw new ApiException(ErrorCode.PAYMENT_DECLINED);
        }
        String paymentKey = "MOCK_PG_" + UUID.randomUUID().toString().substring(0, 12);

        // 4. DB 확정 (UNIQUE 충돌 시 전체 롤백)
        LocalDateTime now = LocalDateTime.now();
        String ticketNumber = String.format("%04d-%04d-%04d-%02d",
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(10, 100));
        final int amount = totalPrice;

        PaymentDTO payment = PaymentDTO.builder()
                .orderId(ticketNumber).paymentKey(paymentKey)
                .amount(amount).status("APPROVED").requestedAt(now).approvedAt(now)
                .build();
        List<Long> reservationIds = transactionTemplate.execute(status -> {
            List<Long> ids = new ArrayList<>();
            holds.forEach((scheduleSeq, seats) -> {
                for (SeatSelection s : seats) {
                    ids.add(confirmSeat(scheduleSeq, s, memberId, unitPrices.get(scheduleSeq), now));
                }
            });
            payment.setReservationSeq(ids.get(0));
            paymentMapper.insertPayment(payment);
            // 커밋이 끝난 뒤에만 실행된다 (PaymentConfirmedListener)
            holds.forEach((scheduleSeq, seats) ->
                    eventPublisher.publishEvent(new PaymentConfirmedEvent(scheduleSeq, memberId, seats)));
            return ids;
        });

        log.info("[Payment Success] 발권 완료: 티켓번호={}, 회원={}, 운행={}, 좌석={}석, 금액={}",
                ticketNumber, memberId, schedules, reservationIds.size(), amount);

        return PaymentResponseDTO.builder()
                .paymentSeq(payment.getPaymentSeq())
                .reservationId(reservationIds.get(0))
                .ticketNumber(ticketNumber)
                .status("APPROVED")
                .amount(amount)
                .approvedAt(now)
                .message("승차권 예매 및 결제가 정상적으로 완료되었습니다.")
                .build();
    }

    /** 좌석 1개 확정: seat 행 INSERT(UNIQUE 최종 방어선) + reservation CONFIRMED */
    private Long confirmSeat(Long scheduleSeq, SeatSelection s, String memberId, int unitPrice, LocalDateTime now) {
        SeatTemplateGenerator.SeatTemplateItem tpl = seatTemplateGenerator.findTemplateSeat(s.getCarNo(), s.getSeatNo());
        String grade = tpl != null ? tpl.getSeatGrade() : SeatTemplateGenerator.GRADE_STANDARD;
        SeatDTO seat = SeatDTO.builder()
                .scheduleSeq(scheduleSeq).carNo(s.getCarNo()).seatNo(s.getSeatNo()).seatGrade(grade)
                .build();
        if (seatMapper.insertOccupiedSeat(seat) == 0 || seat.getSeatSeq() == null) {
            // Redis 선점이 정상이라면 일어나지 않는다. 최종 방어선.
            throw new ApiException(ErrorCode.SEAT_ALREADY_TAKEN,
                    "좌석 " + s.getCarNo() + "호차 " + s.getSeatNo() + "은(는) 이미 판매되었습니다.");
        }
        ReservationDTO reservation = ReservationDTO.builder()
                .memberId(memberId).scheduleSeq(scheduleSeq).seatSeq(seat.getSeatSeq())
                .carNo(s.getCarNo()).seatNo(s.getSeatNo()).seatGrade(grade)
                .status("CONFIRMED").totalPrice(unitPrice)
                .reservedAt(now).confirmedAt(now)
                .build();
        reservationMapper.insertReservation(reservation);
        return reservation.getReservationSeq();
    }
}
