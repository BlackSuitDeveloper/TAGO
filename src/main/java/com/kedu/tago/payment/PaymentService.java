package com.kedu.tago.payment;

import com.kedu.tago.payment.dto.PaymentDTO;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.reservation.ReservationMapper;
import com.kedu.tago.reservation.dto.ReservationDTO;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final SeatService seatService;

    /**
     * 결제 및 발권 확정 (P001, P002: 금액 검증 후 단일 트랜잭션으로 결제/예약/좌석 확정)
     */
    @Transactional
    public PaymentResponseDTO processPayment(PaymentRequestDTO request, String memberId) {
        Long reservationId = request.getReservationId();
        Integer requestedPrice = request.getTotalPrice();

        if (reservationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "예약 번호(reservationId)가 필요합니다.");
        }

        // 1. 예약 조회 및 검증
        ReservationDTO reservation = reservationMapper.selectReservationById(reservationId);
        if (reservation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "예약 내역을 찾을 수 없습니다.");
        }

        if (!"HELD".equals(reservation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 가능한 상태가 아닙니다. 현재 상태: " + reservation.getStatus());
        }

        if (reservation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "좌석 선점 유효시간(5분)이 만료되었습니다. 다시 예매해 주세요.");
        }

        // 2. 서버 금액 위변조 검증 (P001)
        if (requestedPrice != null && !requestedPrice.equals(reservation.getTotalPrice())) {
            log.warn("[Payment Error] 결제 금액 불일치! 요청금액: {}, 실제금액: {}", requestedPrice, reservation.getTotalPrice());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 요청 금액이 유효하지 않습니다.");
        }

        LocalDateTime now = LocalDateTime.now();

        // 3. 발권용 승차권 번호(ticketNumber) 생성 (형식: 1029-3847-5690-11)
        String ticketNumber = String.format("%04d-%04d-%04d-%02d",
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(1000, 10000),
                ThreadLocalRandom.current().nextInt(10, 100)
        );

        // 4. 결제(Payment) 승인 레코드 저장
        PaymentDTO payment = PaymentDTO.builder()
                .reservationSeq(reservationId)
                .orderId(ticketNumber)
                .paymentKey("MOCK_PG_" + UUID.randomUUID().toString().substring(0, 12))
                .amount(reservation.getTotalPrice())
                .status("APPROVED")
                .requestedAt(now)
                .approvedAt(now)
                .build();

        paymentMapper.insertPayment(payment);

        // 5. 예약(Reservation) 확정 (CONFIRMED)
        reservationMapper.confirmReservation(reservationId);

        // 6. 좌석(Seat) 상태 확정 (chk_seat_status 제약: AVAILABLE, HELD, CONFIRMED)
        seatMapper.updateSeatStatusBySeq(reservation.getSeatSeq(), "CONFIRMED");

        log.info("[Payment Success] 승차권 발권 완료: 티켓번호={}, 예약Seq={}, 금액={}",
                ticketNumber, reservationId, reservation.getTotalPrice());

        return PaymentResponseDTO.builder()
                .paymentSeq(payment.getPaymentSeq())
                .reservationId(reservationId)
                .ticketNumber(ticketNumber)
                .status("APPROVED")
                .amount(reservation.getTotalPrice())
                .approvedAt(now)
                .message("승차권 예매 및 결제가 정상적으로 완료되었습니다.")
                .build();
    }
}
