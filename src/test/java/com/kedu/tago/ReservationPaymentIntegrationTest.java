package com.kedu.tago;

import com.kedu.tago.payment.PaymentService;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.reservation.dto.MyTicketResponseDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatService;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.seat.dto.SeatResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ReservationPaymentIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("3번 API 전체 플로우 테스트: 대기열 -> 좌석조회 -> 좌석선점(동시성방지) -> 결제/발권 -> 마이페이지 조회 -> 취소")
    void testFullReservationAndPaymentFlow() {
        // 0. 테스트용 회원 생성 (member_id 기준 FK 제약 충족, phone은 varchar(11))
        jdbcTemplate.update("""
            INSERT INTO member (member_id, pw, name, phone, email, zipcode, address1, address2)
            VALUES ('testUser1', 'pw1234', '테스터1', '01000000001', 'test1@tago.kr', '04320', '서울', '상세')
            ON CONFLICT (member_id) DO NOTHING
        """);
        jdbcTemplate.update("""
            INSERT INTO member (member_id, pw, name, phone, email, zipcode, address1, address2)
            VALUES ('testUser2', 'pw1234', '테스터2', '01000000002', 'test2@tago.kr', '04320', '서울', '상세')
            ON CONFLICT (member_id) DO NOTHING
        """);

        // 1. 테스트용 스케줄 확보 및 대상 좌석(1호차 1A) 기존 점유 데이터 정리
        Long scheduleSeq = jdbcTemplate.queryForObject("SELECT schedule_seq FROM schedule LIMIT 1", Long.class);
        assertNotNull(scheduleSeq, "스케줄 데이터가 존재해야 합니다.");

        jdbcTemplate.update("DELETE FROM payment WHERE reservation_seq IN (SELECT r.reservation_seq FROM reservation r JOIN seat s ON r.seat_seq = s.seat_seq WHERE s.schedule_seq = ? AND s.car_no = 1 AND s.seat_no = '1A')", scheduleSeq);
        jdbcTemplate.update("DELETE FROM reservation WHERE seat_seq IN (SELECT seat_seq FROM seat WHERE schedule_seq = ? AND car_no = 1 AND seat_no = '1A')", scheduleSeq);
        jdbcTemplate.update("DELETE FROM seat WHERE schedule_seq = ? AND car_no = 1 AND seat_no = '1A'", scheduleSeq);

        // 2. 대기열 진입 및 순번 확인 (R001)
        QueueResponseDTO queueEnter = queueService.enterQueue(scheduleSeq, "testUser1");
        assertNotNull(queueEnter.getQueueToken());

        QueueResponseDTO queueCheck = queueService.checkQueueRank(queueEnter.getQueueToken());
        assertNotNull(queueCheck);

        // 3. 좌석 목록 및 호차별 잔여석 조회 (GET /api/seat)
        SeatResponseDTO seatResp = seatService.getSeats(scheduleSeq, 1);
        assertNotNull(seatResp);
        assertEquals(1, seatResp.getCarNo());
        assertTrue(seatResp.getAvailableSeats() > 0);

        // 4. 좌석 선점 (POST /api/reservation - R002)
        ReservationRequestDTO holdReq = ReservationRequestDTO.builder()
                .scheduleSeq(scheduleSeq)
                .carNo(1)
                .seatList(List.of("1A"))
                .count(1)
                .build();

        ReservationResponseDTO holdResp = reservationService.holdSeats(holdReq, "testUser1");
        assertNotNull(holdResp.getReservationSeq());
        assertEquals("HELD", holdResp.getStatus());
        assertNotNull(holdResp.getExpiresAt());

        // 좌석 상태가 HELD로 변경되었는지 확인
        SeatDTO seatAfterHold = seatMapper.selectSeat(scheduleSeq, 1, "1A");
        assertEquals("HELD", seatAfterHold.getStatus());

        // 5. 동시성 제어 검증: 동일 좌석에 대해 다른 사용자가 중복 선점 시도 시 409 Conflict 예외 발생 확인
        assertThrows(ResponseStatusException.class, () -> {
            reservationService.holdSeats(holdReq, "testUser2");
        }, "동일 좌석 중복 선점 요청은 반드시 실패해야 합니다.");

        // 6. 결제 및 발권 확정 (POST /api/payment - P001, P002)
        PaymentRequestDTO payReq = PaymentRequestDTO.builder()
                .reservationId(holdResp.getReservationSeq())
                .payMethod("CARD")
                .totalPrice(holdResp.getTotalPrice())
                .build();

        PaymentResponseDTO payResp = paymentService.processPayment(payReq, "testUser1");
        assertNotNull(payResp.getTicketNumber());
        assertEquals("APPROVED", payResp.getStatus());

        // 좌석 상태가 CONFIRMED로 변경되었는지 확인
        SeatDTO seatAfterPay = seatMapper.selectSeat(scheduleSeq, 1, "1A");
        assertEquals("CONFIRMED", seatAfterPay.getStatus());

        // 7. 내 예매 목록 조회 (MA001)
        List<MyTicketResponseDTO> myTickets = reservationService.getMyReservations("testUser1");
        assertFalse(myTickets.isEmpty());
        MyTicketResponseDTO latestTicket = myTickets.get(0);
        assertEquals("CONFIRMED", latestTicket.getStatus());
        assertEquals(payResp.getTicketNumber(), latestTicket.getTicketNumber());

        // 8. 예매 취소 및 좌석 반환 (MA002)
        reservationService.cancelReservation(holdResp.getReservationSeq(), "testUser1");

        SeatDTO seatAfterCancel = seatMapper.selectSeat(scheduleSeq, 1, "1A");
        assertNull(seatAfterCancel, "취소 후 점유 좌석 레코드는 삭제되어 AVAILABLE 상태가 되어야 합니다.");
    }
}
