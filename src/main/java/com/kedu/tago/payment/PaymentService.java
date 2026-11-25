package com.kedu.tago.payment;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.dto.PaymentDTO;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.reservation.ReservationMapper;
import com.kedu.tago.reservation.dto.ReservationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 결제 및 발권 처리 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final ReservationMapper reservationMapper;
    private final QueueService queueService;

    /** 금액 검증 후 단일 트랜잭션으로 결제 저장 및 예약 확정 */
    @Transactional
    public PaymentResponseDTO processPayment(PaymentRequestDTO request, String memberId) {
        Set<Long> targetIdSet = new LinkedHashSet<>();
        if (request.getReservationIds() != null && !request.getReservationIds().isEmpty()) {
            for (Long id : request.getReservationIds()) {
                if (id != null) targetIdSet.add(id);
            }
        }
        if (request.getReservationId() != null) {
            targetIdSet.add(request.getReservationId());
        }

        if (targetIdSet.isEmpty()) {
            throw new ApiException(ErrorCode.RESERVATION_ID_REQUIRED);
        }

        List<Long> targetIds = new ArrayList<>(targetIdSet);
        Long primaryReservationId = targetIds.get(0);
        Integer requestedPrice = request.getTotalPrice();

        // 1. 예약 조회 및 검증, 합산 금액 계산
        List<ReservationDTO> reservations = new ArrayList<>(targetIds.size());
        int expectedTotalPrice = 0;
        LocalDateTime now = LocalDateTime.now();

        for (Long resId : targetIds) {
            ReservationDTO reservation = reservationMapper.selectReservationById(resId);
            if (reservation == null) {
                throw new ApiException(ErrorCode.RESERVATION_NOT_FOUND);
            }

            // 타인의 예약 번호로 결제 확정하는 것을 차단
            if (memberId == null || !memberId.equals(reservation.getMemberId())) {
                throw new ApiException(ErrorCode.NOT_RESOURCE_OWNER, "본인의 예매만 결제할 수 있습니다.");
            }

            if (!"HELD".equals(reservation.getStatus())) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_AVAILABLE, "결제 가능한 상태가 아닙니다. 현재 상태: " + reservation.getStatus());
            }

            if (reservation.getExpiresAt().isBefore(now)) {
                throw new ApiException(ErrorCode.HOLD_EXPIRED);
            }

            reservations.add(reservation);
            expectedTotalPrice += (reservation.getTotalPrice() != null ? reservation.getTotalPrice() : 0);
        }

        // 2. 금액 위변조 검증
        if (requestedPrice != null && !requestedPrice.equals(expectedTotalPrice)) {
            log.warn("[Payment Error] 결제 금액 불일치! 요청금액: {}, 실제금액: {}", requestedPrice, expectedTotalPrice);
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }

        // 3. 승차권 번호 생성 (형식: 1029-3847-5690-11)
        String ticketNumber = String.format("%04d-%04d-%04d-%02d",
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(10, 100)
        );

        // 4. 결제 승인 저장 (대표 예약 ID 기준 총 금액)
        PaymentDTO payment = PaymentDTO.builder()
                .reservationSeq(primaryReservationId)
                .orderId(ticketNumber)
                .paymentKey("MOCK_PG_" + UUID.randomUUID().toString().substring(0, 12))
                .amount(expectedTotalPrice)
                .status("APPROVED")
                .requestedAt(now)
                .approvedAt(now)
                .build();

        paymentMapper.insertPayment(payment);

        // 5. 전체 예약 조건부 확정 (HELD && 만료 전일 때만 CONFIRMED, 경합 시 롤백)
        for (ReservationDTO reservation : reservations) {
            int updated = reservationMapper.confirmReservation(reservation.getReservationSeq());
            if (updated == 0) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_AVAILABLE, "결제 처리 중 예약이 만료되었거나 이미 처리되었습니다.");
            }
        }
        queueService.releaseSlot(reservations.get(0).getScheduleSeq(), memberId);

        log.info("[Payment Success] 승차권 발권 완료: 티켓번호={}, 예약Seq목록={}, 금액={}",
                ticketNumber, targetIds, expectedTotalPrice);

        return PaymentResponseDTO.builder()
                .paymentSeq(payment.getPaymentSeq())
                .reservationId(primaryReservationId)
                .ticketNumber(ticketNumber)
                .status("APPROVED")
                .amount(expectedTotalPrice)
                .approvedAt(now)
                .message("승차권 예매 및 결제가 정상적으로 완료되었습니다.")
                .build();
    }
}
