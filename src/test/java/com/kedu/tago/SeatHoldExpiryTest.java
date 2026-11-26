package com.kedu.tago;

import com.kedu.tago.common.exception.ApiException;
import com.kedu.tago.common.exception.ErrorCode;
import com.kedu.tago.payment.PaymentService;
import com.kedu.tago.payment.dto.PaymentRequestDTO;
import com.kedu.tago.queue.QueueAdmissionService;
import com.kedu.tago.queue.QueueService;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationRequestDTO.SeatSelection;
import com.kedu.tago.support.ReservationTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 선점 TTL 만료 테스트: TTL 을 2초로 줄여서, 결제하지 않은 좌석이 자동으로 풀리는지 확인 */
@SpringBootTest(properties = {"tago.sync.startup-enabled=false", "tago.queue.max-active=1000", "tago.hold.ttl-seconds=2", "tago.queue.scheduler-enabled=false"})
class SeatHoldExpiryTest {

    private static final int CAR = 18;
    private static final String U1 = "itExpUser1";
    private static final String U2 = "itExpUser2";

    @Autowired private QueueService queueService;
    @Autowired private QueueAdmissionService admission;
    @Autowired private ReservationService reservationService;
    @Autowired private PaymentService paymentService;
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

    private void hold(String member, String seatNo) {
        String token = ReservationTestData.admittedToken(queueService, admission, s, member);
        ReservationRequestDTO req = ReservationRequestDTO.builder()
                .scheduleSeq(s).seats(List.of(new SeatSelection(CAR, seatNo))).build();
        reservationService.holdSeats(req, member, token);
    }

    @Test
    @DisplayName("결제 없이 TTL 이 지나면: 키가 사라지고, 만료 알림 목록도 비워지고, 다른 사람이 선점할 수 있다")
    void holdExpires() throws InterruptedException {
        hold(U1, "4A");
        ApiException before = assertThrows(ApiException.class, () -> hold(U2, "4A"));
        assertEquals(ErrorCode.SEAT_ALREADY_TAKEN, before.getErrorCode());

        Thread.sleep(3500); // TTL 2초 + 만료 알림 스케줄러(1초 주기) 여유

        assertNull(redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":4A"), "TTL 로 키가 사라짐");
        assertNull(redis.opsForZSet().score("held:expiry", s + ":" + CAR + ":4A"), "스케줄러가 알림 후 목록에서 제거");
        hold(U2, "4A"); // 이제 성공
        assertEquals(U2, redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":4A"));
    }

    @Test
    @DisplayName("만료된 뒤 결제하면 410")
    void payAfterExpiryFails() throws InterruptedException {
        hold(U1, "4B");
        Thread.sleep(2500);

        PaymentRequestDTO req = PaymentRequestDTO.builder().scheduleSeq(s).payMethod("CARD").build();
        ApiException e = assertThrows(ApiException.class, () -> paymentService.processPayment(req, U1, "late-key"));
        assertEquals(ErrorCode.HOLD_EXPIRED, e.getErrorCode());
    }

    @Test
    @DisplayName("1인 6석 상한은 지금 유효한 선점만 센다 (만료된 좌석은 빼고 센다)")
    void expiredHoldsDoNotCountTowardLimit() throws InterruptedException {
        for (String seatNo : List.of("1A", "1B", "1C", "1D", "2A")) {
            hold(U1, seatNo);
        }
        Thread.sleep(1200);
        hold(U1, "2B"); // 회원 선점 목록의 TTL 이 다시 늘어난다
        Thread.sleep(1200); // 앞의 5석은 만료, 2B 는 아직 유효

        assertDoesNotThrow(() -> hold(U1, "2C"), "유효한 선점은 1석뿐이므로 더 잡을 수 있어야 한다");
    }
}
