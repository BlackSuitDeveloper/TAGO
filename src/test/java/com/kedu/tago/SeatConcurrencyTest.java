package com.kedu.tago;

import com.kedu.tago.queue.QueueService;
import com.kedu.tago.queue.dto.QueueResponseDTO;
import com.kedu.tago.reservation.ReservationService;
import com.kedu.tago.reservation.dto.ReservationRequestDTO;
import com.kedu.tago.reservation.dto.ReservationResponseDTO;
import com.kedu.tago.seat.SeatMapper;
import com.kedu.tago.seat.dto.SeatDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SeatConcurrencyTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private SeatMapper seatMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("회원 10명 동시 대기열 진입(Redis) + 단일 좌석 동시 선점 테스트")
    void testTenUsersConcurrentSeatHold() throws InterruptedException {
        int userCount = 10;
        String targetSeatNo = "1B";

        // 1. 임의의 회원 10명 생성
        List<String> memberIds = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            String memberId = "concUser" + i;
            memberIds.add(memberId);
            String phone = String.format("0109999%04d", i);
            jdbcTemplate.update("""
                INSERT INTO member (member_id, pw, name, phone, email, zipcode, address1, address2)
                VALUES (?, 'pw1234', ?, ?, ?, '04320', '서울', '테스트')
                ON CONFLICT (member_id) DO NOTHING
            """, memberId, "동시성테스터" + i, phone, memberId + "@tago.kr");
        }

        // 2. 테스트 대상 스케줄 및 좌석 1건 확보 및 초기화
        Long scheduleSeq = jdbcTemplate.queryForObject("SELECT schedule_seq FROM schedule LIMIT 1", Long.class);
        assertNotNull(scheduleSeq, "테스트할 schedule 데이터가 존재해야 합니다.");

        Long seatSeq = jdbcTemplate.queryForObject(
                "SELECT seat_seq FROM seat WHERE schedule_seq = ? AND car_no = 1 AND seat_no = ?",
                Long.class, scheduleSeq, targetSeatNo
        );
        assertNotNull(seatSeq, "테스트할 좌석(1호차 " + targetSeatNo + ")이 존재해야 합니다.");

        jdbcTemplate.update("DELETE FROM payment WHERE reservation_seq IN (SELECT reservation_seq FROM reservation WHERE seat_seq = ?)", seatSeq);
        jdbcTemplate.update("DELETE FROM reservation WHERE seat_seq = ?", seatSeq);
        jdbcTemplate.update("UPDATE seat SET status = 'AVAILABLE' WHERE seat_seq = ?", seatSeq);

        String queueKey = "tago:queue:" + scheduleSeq;
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(queueKey);
            } catch (Exception ignored) {
            }
        }

        // 3. 10명 동시 실행 준비 (CountDownLatch로 정확히 동시에 출발)
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch readyLatch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);
        List<String> winners = Collections.synchronizedList(new ArrayList<>());
        List<String> queueTokens = Collections.synchronizedList(new ArrayList<>());
        List<String> errorLogs = Collections.synchronizedList(new ArrayList<>());

        try {
            for (String memberId : memberIds) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        // 3-1. 대기열 진입 (Redis ZSet)
                        QueueResponseDTO qResp = queueService.enterQueue(scheduleSeq, memberId);
                        if (qResp != null && qResp.getQueueToken() != null) {
                            queueTokens.add(qResp.getQueueToken());
                        }

                        // 3-2. 동일 좌석(1호차 1B) 동시 선점 시도
                        ReservationRequestDTO req = ReservationRequestDTO.builder()
                                .scheduleSeq(scheduleSeq)
                                .carNo(1)
                                .seatList(List.of(targetSeatNo))
                                .count(1)
                                .build();

                        ReservationResponseDTO res = reservationService.holdSeats(req, memberId);
                        if (res != null && "HELD".equals(res.getStatus())) {
                            successCount.incrementAndGet();
                            winners.add(memberId + " (reservationSeq=" + res.getReservationSeq() + ")");
                        }
                    } catch (ResponseStatusException e) {
                        if (e.getStatusCode().value() == 409) {
                            conflictCount.incrementAndGet();
                        } else {
                            otherErrorCount.incrementAndGet();
                            errorLogs.add(memberId + ": HTTP " + e.getStatusCode() + " - " + e.getReason());
                        }
                    } catch (Exception e) {
                        otherErrorCount.incrementAndGet();
                        errorLogs.add(memberId + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown(); // 10명 동시 출발!
            doneLatch.await();
        } finally {
            executor.shutdown();
        }

        // 4. Redis 실제 저장 여부 확인
        Long redisQueueSize = null;
        String redisCheckNote = "RedisTemplate 미주입";
        if (redisTemplate != null) {
            try {
                redisQueueSize = redisTemplate.opsForZSet().zCard(queueKey);
                redisCheckNote = "Redis 연결 성공 (ZSet " + queueKey + " 크기=" + redisQueueSize + ")";
            } catch (Exception e) {
                redisCheckNote = "Redis 연결 실패/예외: " + e.getMessage();
            }
        }

        // 5. DB 정합성 확인
        SeatDTO finalSeat = seatMapper.selectSeat(scheduleSeq, 1, targetSeatNo);
        Integer reservationRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE seat_seq = ? AND status = 'HELD'",
                Integer.class, seatSeq
        );

        System.out.println("===================================================");
        System.out.println("[10명 동시 좌석 선점 테스트 결과]");
        System.out.println("대상 스케줄: " + scheduleSeq + " / 대상 좌석: 1호차 " + targetSeatNo);
        System.out.println("Redis 상태: " + redisCheckNote);
        System.out.println("대기열 토큰 발급 수: " + queueTokens.size() + " / 10");
        System.out.println("좌석 선점 성공: " + successCount.get() + "명 -> " + winners);
        System.out.println("좌석 선점 충돌(409 Conflict): " + conflictCount.get() + "명");
        System.out.println("기타 에러: " + otherErrorCount.get() + "건 " + errorLogs);
        System.out.println("최종 DB seat.status: " + (finalSeat != null ? finalSeat.getStatus() : "null"));
        System.out.println("최종 DB reservation(HELD) 건수: " + reservationRowCount);
        System.out.println("===================================================");

        // 6. 테스트 종료 후 원상 복구 (DB/Redis 청결 유지)
        jdbcTemplate.update("DELETE FROM payment WHERE reservation_seq IN (SELECT reservation_seq FROM reservation WHERE seat_seq = ?)", seatSeq);
        jdbcTemplate.update("DELETE FROM reservation WHERE seat_seq = ?", seatSeq);
        jdbcTemplate.update("UPDATE seat SET status = 'AVAILABLE' WHERE seat_seq = ?", seatSeq);
        for (String memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(queueKey);
                if (!queueTokens.isEmpty()) {
                    redisTemplate.opsForSet().remove("tago:queue:admitted", queueTokens.toArray());
                }
            } catch (Exception ignored) {
            }
        }

        // 검증 단언
        assertEquals(1, successCount.get(), "동시 요청 10명 중 정확히 1명만 선점에 성공해야 합니다.");
        assertEquals(9, conflictCount.get(), "나머지 9명은 409 Conflict를 받아야 합니다.");
        assertEquals(0, otherErrorCount.get(), "예상치 못한 오류가 없어야 합니다: " + errorLogs);
        assertEquals("HELD", finalSeat.getStatus());
        assertEquals(1, reservationRowCount);
    }

    @Test
    @DisplayName("육안 확인용: EX 180초 걸고 10명 동시 예매 시도 (Redis 키 유지)")
    void testInspectInRedisFor180Seconds() throws InterruptedException {
        int userCount = 10;
        String targetSeatNo = "1B";

        List<String> memberIds = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            String memberId = "concUser" + i;
            memberIds.add(memberId);
            String phone = String.format("0109999%04d", i);
            jdbcTemplate.update("""
                INSERT INTO member (member_id, pw, name, phone, email, zipcode, address1, address2)
                VALUES (?, 'pw1234', ?, ?, ?, '04320', '서울', '테스트')
                ON CONFLICT (member_id) DO NOTHING
            """, memberId, "동시성테스터" + i, phone, memberId + "@tago.kr");
        }

        Long scheduleSeq = jdbcTemplate.queryForObject("SELECT schedule_seq FROM schedule LIMIT 1", Long.class);
        Long seatSeq = jdbcTemplate.queryForObject(
                "SELECT seat_seq FROM seat WHERE schedule_seq = ? AND car_no = 1 AND seat_no = ?",
                Long.class, scheduleSeq, targetSeatNo
        );

        jdbcTemplate.update("DELETE FROM payment WHERE reservation_seq IN (SELECT reservation_seq FROM reservation WHERE seat_seq = ?)", seatSeq);
        jdbcTemplate.update("DELETE FROM reservation WHERE seat_seq = ?", seatSeq);
        jdbcTemplate.update("UPDATE seat SET status = 'AVAILABLE' WHERE seat_seq = ?", seatSeq);

        String queueKey = "tago:queue:" + scheduleSeq;
        String seatLockKey = "tago:seat:hold:" + scheduleSeq + ":1:" + targetSeatNo;
        assertNotNull(redisTemplate, "StringRedisTemplate이 주입되어야 합니다.");
        redisTemplate.delete(queueKey);
        redisTemplate.delete("tago:queue:admitted");
        redisTemplate.delete(seatLockKey);

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch readyLatch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<String> winners = Collections.synchronizedList(new ArrayList<>());

        try {
            for (String memberId : memberIds) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        // 1. Redis ZSet 대기열 진입
                        queueService.enterQueue(scheduleSeq, memberId);

                        // 2. Redis SET NX EX 180으로 좌석 선점 락 경합
                        Boolean lockAcquired = redisTemplate.opsForValue()
                                .setIfAbsent(seatLockKey, memberId, Duration.ofSeconds(180));
                        if (!Boolean.TRUE.equals(lockAcquired)) {
                            conflictCount.incrementAndGet();
                            return;
                        }

                        // 3. 락 획득한 1명만 DB 예매 생성
                        ReservationRequestDTO req = ReservationRequestDTO.builder()
                                .scheduleSeq(scheduleSeq)
                                .carNo(1)
                                .seatList(List.of(targetSeatNo))
                                .count(1)
                                .build();
                        ReservationResponseDTO res = reservationService.holdSeats(req, memberId);
                        successCount.incrementAndGet();
                        winners.add(memberId + " (reservationSeq=" + res.getReservationSeq() + ")");
                    } catch (ResponseStatusException e) {
                        if (e.getStatusCode().value() == 409) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
        } finally {
            executor.shutdown();
        }

        // 육안 확인을 위해 대기열 키들에도 TTL 180초 부여 (삭제하지 않고 남겨둠!)
        redisTemplate.expire(queueKey, Duration.ofSeconds(180));
        redisTemplate.expire("tago:queue:admitted", Duration.ofSeconds(180));

        Long queueSize = redisTemplate.opsForZSet().zCard(queueKey);
        String lockOwner = redisTemplate.opsForValue().get(seatLockKey);
        Long lockTtl = redisTemplate.getExpire(seatLockKey);

        System.out.println("===================================================");
        System.out.println("[EX 180초 육안 확인용 테스트 완료 - Redis 키 삭제 안 함]");
        System.out.println("1) 대기열 키: " + queueKey + " (인원=" + queueSize + "명, TTL=180초)");
        System.out.println("2) 통과자 키: tago:queue:admitted (TTL=180초)");
        System.out.println("3) 좌석 락 키: " + seatLockKey + " = " + lockOwner + " (남은 TTL=" + lockTtl + "초)");
        System.out.println("선점 성공: " + successCount.get() + "명 -> " + winners);
        System.out.println("선점 충돌(차단): " + conflictCount.get() + "명");
        System.out.println("===================================================");

        assertEquals(1, successCount.get());
        assertEquals(9, conflictCount.get());
        assertNotNull(lockOwner);
    }

    @Test
    @DisplayName("ZSet 줄세우기 테스트: 10명 동시 진입 시 currentTime 기준 정렬 확인 (Redis 데이터 유지)")
    void testZSetQueueOrdering() throws InterruptedException {
        int userCount = 10;
        String queueKey = "tago:queue:order-test";

        assertNotNull(redisTemplate, "StringRedisTemplate이 주입되어야 합니다.");
        redisTemplate.delete(queueKey);

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch readyLatch = new CountDownLatch(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        try {
            for (int i = 1; i <= userCount; i++) {
                final String memberId = String.format("user%02d", i);
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        long enterTime = System.currentTimeMillis();
                        redisTemplate.opsForZSet().addIfAbsent(queueKey, memberId, enterTime);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown(); // 10명 동시 진입!
            doneLatch.await();
        } finally {
            executor.shutdown();
        }

        var ordered = redisTemplate.opsForZSet().rangeWithScores(queueKey, 0, -1);
        assertNotNull(ordered);
        assertEquals(userCount, ordered.size(), "10명 모두 ZSet 대기열에 등록되어야 합니다.");

        System.out.println("===================================================");
        System.out.println("[ZSet 줄세우기 결과 - 키: " + queueKey + " (삭제 안 함)]");
        int rank = 1;
        double prevScore = -1;
        for (ZSetOperations.TypedTuple<String> entry : ordered) {
            double score = entry.getScore() != null ? entry.getScore() : 0;
            assertTrue(score >= prevScore, "ZSet은 currentTime(score) 오름차순으로 정렬되어야 합니다.");
            prevScore = score;
            System.out.printf("%2d등 | %s | score(currentTime)=%.0f%n", rank++, entry.getValue(), score);
        }
        System.out.println("===================================================");
    }

    @Test
    @DisplayName("임의의 유저 50명 추가 + 한 좌석 1ms 간격 예매 시도 및 Redis ZSet 스코어링 검증")
    void testFiftyUsers1msIntervalReservationAndRedisScoring() throws InterruptedException {
        int userCount = 50;
        String targetSeatNo = "1B";

        assertNotNull(redisTemplate, "StringRedisTemplate이 주입되어야 합니다.");

        // 1. 임의의 회원 50명 DB 추가
        List<String> memberIds = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            String memberId = String.format("test50User%02d", i);
            memberIds.add(memberId);
            String phone = String.format("0108888%04d", i);
            jdbcTemplate.update("""
                INSERT INTO member (member_id, pw, name, phone, email, zipcode, address1, address2)
                VALUES (?, 'pw1234', ?, ?, ?, '04320', '서울', '테스트')
                ON CONFLICT (member_id) DO NOTHING
            """, memberId, "50인테스터" + i, phone, memberId + "@tago.kr");
        }

        // 2. 테스트 대상 스케줄 및 좌석 1건 확보 및 초기화
        Long scheduleSeq = jdbcTemplate.queryForObject("SELECT schedule_seq FROM schedule LIMIT 1", Long.class);
        assertNotNull(scheduleSeq, "테스트할 schedule 데이터가 존재해야 합니다.");

        Long seatSeq = jdbcTemplate.queryForObject(
                "SELECT seat_seq FROM seat WHERE schedule_seq = ? AND car_no = 1 AND seat_no = ?",
                Long.class, scheduleSeq, targetSeatNo
        );
        assertNotNull(seatSeq, "테스트할 좌석(1호차 " + targetSeatNo + ")이 존재해야 합니다.");

        jdbcTemplate.update("DELETE FROM payment WHERE reservation_seq IN (SELECT reservation_seq FROM reservation WHERE seat_seq = ?)", seatSeq);
        jdbcTemplate.update("DELETE FROM reservation WHERE seat_seq = ?", seatSeq);
        jdbcTemplate.update("UPDATE seat SET status = 'AVAILABLE' WHERE seat_seq = ?", seatSeq);

        String queueKey = "tago:queue:" + scheduleSeq;
        redisTemplate.delete(queueKey);
        redisTemplate.delete("tago:queue:admitted");

        // 3. 50명 1ms 간격으로 대기열 진입 + 동일 좌석 예매 시도
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch doneLatch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);
        List<String> winners = Collections.synchronizedList(new ArrayList<>());
        List<String> queueTokens = Collections.synchronizedList(new ArrayList<>());
        List<String> tokenUserPairs = Collections.synchronizedList(new ArrayList<>());
        List<String> errorLogs = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < userCount; i++) {
                final String memberId = memberIds.get(i);
                final int orderIndex = i + 1;

                executor.submit(() -> {
                    try {
                        // 3-1. 대기열 진입 (Redis ZSet 스코어링)
                        QueueResponseDTO qResp = queueService.enterQueue(scheduleSeq, memberId);
                        if (qResp != null && qResp.getQueueToken() != null) {
                            queueTokens.add(qResp.getQueueToken());
                            tokenUserPairs.add(String.format("#%02d %s -> token=%s", orderIndex, memberId, qResp.getQueueToken()));
                        }

                        // 3-2. 동일 좌석(1호차 1B) 예매(선점) 시도
                        ReservationRequestDTO req = ReservationRequestDTO.builder()
                                .scheduleSeq(scheduleSeq)
                                .carNo(1)
                                .seatList(List.of(targetSeatNo))
                                .count(1)
                                .build();

                        ReservationResponseDTO res = reservationService.holdSeats(req, memberId);
                        if (res != null && "HELD".equals(res.getStatus())) {
                            successCount.incrementAndGet();
                            winners.add(memberId + " (reservationSeq=" + res.getReservationSeq() + ")");
                        }
                    } catch (ResponseStatusException e) {
                        if (e.getStatusCode().value() == 409) {
                            conflictCount.incrementAndGet();
                        } else {
                            otherErrorCount.incrementAndGet();
                            errorLogs.add(memberId + ": HTTP " + e.getStatusCode() + " - " + e.getReason());
                        }
                    } catch (Exception e) {
                        otherErrorCount.incrementAndGet();
                        errorLogs.add(memberId + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });

                // 1ms 간격으로 다음 유저 요청 전송
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }

            doneLatch.await();
        } finally {
            executor.shutdown();
        }

        // 4. Redis ZSet 스코어링 실제 결과 조회
        var orderedScores = redisTemplate.opsForZSet().rangeWithScores(queueKey, 0, -1);
        assertNotNull(orderedScores);

        System.out.println("===================================================");
        System.out.println("[50명 1ms 간격 예매 시도 & Redis ZSet 스코어링 결과]");
        System.out.println("대상 스케줄: " + scheduleSeq + " / 대상 좌석: 1호차 " + targetSeatNo);
        System.out.println("Redis ZSet 키: " + queueKey + " (총 등록 수: " + orderedScores.size() + " / 50)");
        System.out.println("선점 성공: " + successCount.get() + "명 -> " + winners);
        System.out.println("선점 충돌(409 Conflict): " + conflictCount.get() + "명");
        System.out.println("기타 에러: " + otherErrorCount.get() + "건 " + errorLogs);
        System.out.println("---------------------------------------------------");
        System.out.println("[Redis ZSet 스코어링 상위/전체 목록 (오름차순)]");

        int rank = 1;
        double prevScore = -1;
        double firstScore = -1;
        double lastScore = -1;
        for (ZSetOperations.TypedTuple<String> entry : orderedScores) {
            double score = entry.getScore() != null ? entry.getScore() : 0;
            if (firstScore < 0) firstScore = score;
            lastScore = score;
            assertTrue(score >= prevScore, "ZSet 스코어는 오름차순이어야 합니다.");
            prevScore = score;
            System.out.printf("%2d등 | token=%s | score=%.0f (+%.0fms)%n",
                    rank++, entry.getValue(), score, (score - firstScore));
        }
        System.out.printf("총 스코어링 시간 범위: %.0fms (첫 진입 ~ 마지막 진입)%n", (lastScore - firstScore));
        System.out.println("===================================================");

        // 육안 확인을 위해 Redis 키에 TTL 180초 부여 후 유지
        redisTemplate.expire(queueKey, Duration.ofSeconds(180));
        redisTemplate.expire("tago:queue:admitted", Duration.ofSeconds(180));

        // 5. DB 정합성 검증 및 테스트 데이터 정리
        SeatDTO finalSeat = seatMapper.selectSeat(scheduleSeq, 1, targetSeatNo);
        Integer reservationRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE seat_seq = ? AND status = 'HELD'",
                Integer.class, seatSeq
        );

        assertEquals(userCount, orderedScores.size(), "50명 모두 Redis ZSet에 스코어링되어 등록되어야 합니다.");
        assertEquals(1, successCount.get(), "50명 중 정확히 1명만 좌석 선점에 성공해야 합니다.");
        assertEquals(userCount - 1, conflictCount.get(), "나머지 49명은 409 Conflict를 받아야 합니다.");
        assertEquals(0, otherErrorCount.get(), "예상치 못한 오류가 없어야 합니다: " + errorLogs);
        assertEquals("HELD", finalSeat.getStatus());
        assertEquals(1, reservationRowCount);

        // DB 원상 복구 (테스트용 회원/예약/좌석 초기화)
        jdbcTemplate.update("DELETE FROM payment WHERE reservation_seq IN (SELECT reservation_seq FROM reservation WHERE seat_seq = ?)", seatSeq);
        jdbcTemplate.update("DELETE FROM reservation WHERE seat_seq = ?", seatSeq);
        jdbcTemplate.update("UPDATE seat SET status = 'AVAILABLE' WHERE seat_seq = ?", seatSeq);
        for (String memberId : memberIds) {
            jdbcTemplate.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }
}
