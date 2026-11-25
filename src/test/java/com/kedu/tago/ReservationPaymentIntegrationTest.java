package com.kedu.tago;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.PaymentService;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.payment.dto.PaymentResponseDTO;
import com.kedu.tago.queue.QueueAdmissionService;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import com.kedu.tago.seat.SeatService;
import com.kedu.tago.seat.dto.SeatDTO;
import com.kedu.tago.support.ReservationTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 예매 전체 흐름 통합 테스트 (실제 PostgreSQL + Redis 필요: docker compose up -d postgres redis)
 * 대기열 → Redis 선점 → 충돌 → 좌석 조회 → 결제(멱등키) → 재요청 → 결제 거절 → 환불 후 재판매
 */
@SpringBootTest(properties = {"tago.sync.startup-enabled=false", "tago.queue.max-active=1000", "tago.queue.scheduler-enabled=false"})
class ReservationPaymentIntegrationTest {

    private static final int CAR = 18;
    private static final String U1 = "itPayUser1";
    private static final String U2 = "itPayUser2";

    @Autowired private QueueService queueService;
    @Autowired private QueueAdmissionService admission;
    @Autowired private ReservationService reservationService;
    @Autowired private PaymentService paymentService;
    @Autowired private SeatService seatService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;

    private Long s;

    @BeforeEach
    void setUp() {
        s = ReservationTestData.bookableSchedule(jdbc);
        ReservationTestData.clean(jdbc, redis, s, CAR, U1, U2);
    }

    @AfterEach
    void tearDown() {
        ReservationTestData.clean(jdbc, redis, s, CAR, U1, U2);
    }

    private ReservationResponseDTO hold(String member, String... seatNos) {
        String token = ReservationTestData.admittedToken(queueService, admission, s, member);
        ReservationRequestDTO req = ReservationRequestDTO.builder()
                .scheduleSeq(s)
                .seats(Arrays.stream(seatNos).map(no -> new SeatSelection(CAR, no)).toList())
                .build();
        return reservationService.holdSeats(req, member, token);
    }

    private PaymentResponseDTO pay(String member, String key, String mockResult) {
        PaymentRequestDTO req = PaymentRequestDTO.builder().scheduleSeq(s).payMethod("CARD").mockResult(mockResult).build();
        return paymentService.processPayment(req, member, key);
    }

    private Map<String, String> seatStatuses() {
        return seatService.getSeats(s, CAR).getSeatList().stream()
                .collect(Collectors.toMap(SeatDTO::getSeatNo, SeatDTO::getStatus));
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("선점 → 충돌 → 조회 → 결제(멱등) → 재요청 → 거절 → 환불 후 재판매")
    void fullFlow() {
        // 1. 선점: Redis 에만 기록되고 DB 에는 아무것도 없다
        ReservationResponseDTO held = hold(U1, "1A", "1B");
        assertEquals("HELD", held.getStatus());
        assertNotNull(held.getExpiresAt());
        assertEquals(U1, redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":1A"));
        assertEquals(0, count("SELECT count(*) FROM seat WHERE schedule_seq = ? AND car_no = ?", s, CAR));

        // 2. 다른 사람이 겹치는 좌석을 잡으면 409
        ApiException conflict = assertThrows(ApiException.class, () -> hold(U2, "1B", "1C"));
        assertEquals(ErrorCode.SEAT_ALREADY_TAKEN, conflict.getErrorCode());
        assertNull(redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":1C"), "실패한 요청의 좌석은 하나도 잡히면 안 된다");

        // 3. 좌석 조회에 HELD 로 보인다
        assertEquals("HELD", seatStatuses().get("1A"));
        assertEquals("AVAILABLE", seatStatuses().get("1C"));

        // 4. 결제 → DB 에 CONFIRMED 2건, Redis 선점 키는 커밋 후 삭제
        PaymentResponseDTO paid = pay(U1, "key-1", null);
        assertEquals("APPROVED", paid.getStatus());
        assertEquals(2, count("SELECT count(*) FROM reservation WHERE schedule_seq = ? AND member_id = ? AND status = 'CONFIRMED'", s, U1));
        assertNull(redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":1A"));
        assertEquals("CONFIRMED", seatStatuses().get("1A"));

        // 5. 같은 멱등키로 다시 보내면 같은 결과, 결제는 1건 그대로
        PaymentResponseDTO replay = pay(U1, "key-1", null);
        assertEquals(paid.getTicketNumber(), replay.getTicketNumber());
        assertEquals(1, count("SELECT count(*) FROM payment p JOIN reservation r USING (reservation_seq) WHERE r.schedule_seq = ? AND r.member_id = ?", s, U1));

        // 6. 다른 키로 또 결제해도 선점이 없으므로 410 (이중 결제 불가)
        ApiException again = assertThrows(ApiException.class, () -> pay(U1, "key-2", null));
        assertEquals(ErrorCode.HOLD_EXPIRED, again.getErrorCode());

        // 7. 이미 팔린 좌석은 선점 불가 (Redis 메모는 지워졌지만 DB 확인으로 막힘)
        ApiException sold = assertThrows(ApiException.class, () -> hold(U2, "1A"));
        assertEquals(ErrorCode.SEAT_ALREADY_TAKEN, sold.getErrorCode());

        // 8. 결제 거절 → 402, 선점 즉시 반납
        hold(U2, "2A");
        ApiException declined = assertThrows(ApiException.class, () -> pay(U2, "key-3", "FAIL"));
        assertEquals(ErrorCode.PAYMENT_DECLINED, declined.getErrorCode());
        assertNull(redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":2A"));
        assertEquals("AVAILABLE", seatStatuses().get("2A"));

        // 9. 환불(예매 취소) → seat 행 삭제 → 다른 사람이 다시 살 수 있다
        Long reservation1A = jdbc.queryForObject(
                "SELECT reservation_seq FROM reservation WHERE schedule_seq = ? AND car_no = ? AND seat_no = '1A'", Long.class, s, CAR);
        reservationService.cancelReservation(reservation1A, U1);
        assertEquals("HELD", hold(U2, "1A").getStatus());
    }
}
