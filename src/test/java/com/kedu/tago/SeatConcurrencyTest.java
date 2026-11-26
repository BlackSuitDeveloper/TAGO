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
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.SeatTemplateGenerator;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 동시성 통합 테스트 (실제 PostgreSQL + Redis 필요).
 * 여러 스레드를 "출발 신호"에 맞춰 동시에 출발시켜, 락·선점·멱등키가 경쟁 상황에서도 지켜지는지 본다.
 */
@SpringBootTest(properties = {"tago.sync.startup-enabled=false", "tago.queue.max-active=1000", "tago.queue.scheduler-enabled=false"})
class SeatConcurrencyTest {

    private static final int CAR = 18;
    private static final int USERS = 30;

    @Autowired private QueueService queueService;
    @Autowired private QueueAdmissionService admission;
    @Autowired private ReservationService reservationService;
    @Autowired private PaymentService paymentService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SeatMapper seatMapper;
    @Autowired private StringRedisTemplate redis;

    private Long s;
    private final String[] members = new String[USERS];

    @BeforeEach
    void setUp() {
        for (int i = 0; i < USERS; i++) {
            members[i] = String.format("itConUser%02d", i);
        }
        s = ReservationTestData.bookableSchedule(jdbc);
        ReservationTestData.clean(jdbc, redis, s, CAR, members);
    }

    @AfterEach
    void tearDown() {
        ReservationTestData.clean(jdbc, redis, s, CAR, members);
    }

    private String heldBy(String seatNo) {
        return redis.opsForValue().get("held:seat:" + s + ":" + CAR + ":" + seatNo);
    }

    /** n개 작업을 출발 신호에 맞춰 동시에 실행하고, 성공한 작업 번호 → 결과 를 돌려준다 */
    private <T> Map<Integer, Object> runConcurrently(int n, IntFunction<T> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        Map<Integer, Object> results = new ConcurrentHashMap<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    results.put(idx, task.apply(idx));
                } catch (ApiException e) {
                    results.put(idx, e.getErrorCode());
                } catch (Exception e) {
                    results.put(idx, e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown(); // 출발!
        assertTrue(done.await(30, TimeUnit.SECONDS), "제한 시간 안에 끝나야 한다");
        pool.shutdown();
        return results;
    }

    private String[] enterAll() {
        String[] tokens = new String[USERS];
        for (int i = 0; i < USERS; i++) {
            tokens[i] = queueService.enterQueue(s, members[i]).getQueueToken();
        }
        ReservationTestData.admitAll(admission, s);
        return tokens;
    }

    private ReservationRequestDTO request(String... seatNos) {
        List<SeatSelection> seats = new ArrayList<>();
        for (String no : seatNos) {
            seats.add(new SeatSelection(CAR, no));
        }
        return ReservationRequestDTO.builder().scheduleSeq(s).seats(seats).build();
    }

    @Test
    @DisplayName("30명이 같은 좌석을 동시에 선점: 정확히 1명만 성공, 나머지는 409")
    void sameSeatOnlyOneWins() throws InterruptedException {
        String[] tokens = enterAll();

        Map<Integer, Object> results = runConcurrently(USERS,
                i -> reservationService.holdSeats(request("1A"), members[i], tokens[i]));

        List<Integer> winners = results.entrySet().stream()
                .filter(e -> !(e.getValue() instanceof ErrorCode) && !(e.getValue() instanceof Exception))
                .map(Map.Entry::getKey).toList();
        long conflicts = results.values().stream().filter(v -> v == ErrorCode.SEAT_ALREADY_TAKEN).count();

        assertEquals(1, winners.size(), "성공은 정확히 1명");
        assertEquals(USERS - 1, conflicts, "나머지는 모두 409");
        assertEquals(members[winners.get(0)], heldBy("1A"), "Redis 선점 주인 = 성공한 사람");
    }

    @Test
    @DisplayName("겹치는 2석 묶음을 30명이 동시에 선점: 한 좌석이 두 사람에게 가지 않고, 실패한 요청은 하나도 잡지 않는다")
    void overlappingSeatsNeverDoubleHeld() throws InterruptedException {
        String[] tokens = enterAll();
        String[] pool = {"1A", "1B", "1C", "1D"};

        Map<Integer, Object> results = runConcurrently(USERS,
                i -> reservationService.holdSeats(request(pool[i % 4], pool[(i + 1) % 4]), members[i], tokens[i]));

        Set<String> claimed = new HashSet<>();
        for (Map.Entry<Integer, Object> e : results.entrySet()) {
            if (e.getValue() instanceof ErrorCode code) {
                assertEquals(ErrorCode.SEAT_ALREADY_TAKEN, code);
                continue;
            }
            assertTrue(!(e.getValue() instanceof Exception), "예상 밖 예외: " + e.getValue());
            int i = e.getKey();
            for (String seat : List.of(pool[i % 4], pool[(i + 1) % 4])) {
                assertTrue(claimed.add(seat), "좌석 " + seat + " 이 두 번 선점됨");
                assertEquals(members[i], heldBy(seat), "성공한 사람의 좌석은 모두 그 사람 것");
            }
        }
        assertFalse(claimed.isEmpty(), "최소 1명은 성공해야 한다");
        // 실패한 요청이 일부만 잡아 둔 좌석이 없어야 한다
        for (String seat : pool) {
            String owner = heldBy(seat);
            assertTrue(owner == null || claimed.contains(seat), "주인 없는 반쪽 선점: " + seat);
        }
    }

    @Test
    @DisplayName("같은 멱등키로 결제 10번 동시 요청(더블클릭): 결제·확정은 딱 1번")
    void concurrentPaymentSameKeyPaysOnce() throws InterruptedException {
        String member = members[0];
        String token = ReservationTestData.admittedToken(queueService, admission, s, member);
        reservationService.holdSeats(request("2A"), member, token);
        PaymentRequestDTO req = PaymentRequestDTO.builder().scheduleSeq(s).payMethod("CARD").build();

        Map<Integer, Object> results = runConcurrently(10, i -> paymentService.processPayment(req, member, "double-click-key"));

        Set<String> tickets = new HashSet<>();
        for (Object r : results.values()) {
            if (r instanceof PaymentResponseDTO res) {
                tickets.add(res.getTicketNumber());
            } else {
                assertEquals(ErrorCode.PAYMENT_IN_PROGRESS, r, "성공이 아니면 '처리 중'(409)이어야 한다");
            }
        }
        assertEquals(1, tickets.size(), "성공 응답은 모두 같은 승차권");
        Integer payments = jdbc.queryForObject(
                "SELECT count(*) FROM payment p JOIN reservation r USING (reservation_seq) WHERE r.schedule_seq = ? AND r.member_id = ?",
                Integer.class, s, member);
        Integer confirmed = jdbc.queryForObject(
                "SELECT count(*) FROM reservation WHERE schedule_seq = ? AND member_id = ? AND status = 'CONFIRMED'",
                Integer.class, s, member);
        assertEquals(1, payments, "결제는 1건");
        assertEquals(1, confirmed, "확정 예약은 1건");
    }

    @Test
    @DisplayName("30명이 4석을 동시에 노리고 성공한 사람은 바로 결제: 선점·결제를 거쳐 같은 좌석이 두 번 확정되지 않는다")
    void holdAndPayNoDuplicateSale() throws InterruptedException {
        String[] tokens = enterAll();
        String[] pool = {"3A", "3B", "3C", "3D"};

        Map<Integer, Object> results = runConcurrently(USERS, i -> {
            reservationService.holdSeats(request(pool[i % 4]), members[i], tokens[i]);
            PaymentRequestDTO req = PaymentRequestDTO.builder().scheduleSeq(s).payMethod("CARD").build();
            return paymentService.processPayment(req, members[i], "key-" + i);
        });

        long paid = results.values().stream().filter(v -> v instanceof PaymentResponseDTO).count();
        List<String> sold = jdbc.queryForList(
                "SELECT seat_no FROM reservation WHERE schedule_seq = ? AND car_no = ? AND status = 'CONFIRMED'", String.class, s, CAR);
        assertEquals(paid, sold.size(), "결제 성공 수 = 확정 좌석 수");
        assertEquals(new HashSet<>(sold).size(), sold.size(), "중복 확정 없음");
        assertTrue(sold.size() <= 4, "4석보다 많이 팔릴 수 없음");
        assertTrue(paid > 0, "최소 1명은 결제해야 한다");
    }

    @Test
    @DisplayName("DB 최종 방어선: 같은 좌석 seat 행을 동시에 30번 INSERT 하면 정확히 1건만 들어간다 (Redis 선점을 건너뛴 경우)")
    void dbUniqueIsLastLineOfDefense() throws InterruptedException {
        Map<Integer, Object> results = runConcurrently(USERS, i -> seatMapper.insertOccupiedSeat(
                SeatDTO.builder().scheduleSeq(s).carNo(CAR).seatNo("4A").seatGrade(SeatTemplateGenerator.GRADE_STANDARD).build()));

        long inserted = results.values().stream().filter(v -> Integer.valueOf(1).equals(v)).count();
        long skipped = results.values().stream().filter(v -> Integer.valueOf(0).equals(v)).count();
        assertEquals(1, inserted, "INSERT 성공은 1건");
        assertEquals(USERS - 1, skipped, "나머지는 ON CONFLICT DO NOTHING 으로 0건");
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM seat WHERE schedule_seq = ? AND car_no = ? AND seat_no = '4A'", Integer.class, s, CAR);
        assertEquals(1, rows);
    }
}
