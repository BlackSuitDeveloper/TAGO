package com.kedu.tago.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

/** 예매 통합 테스트 공용: 예매 가능한 열차 찾기, 테스트 데이터(DB·Redis) 정리 */
public final class ReservationTestData {

    private ReservationTestData() {
    }

    /** 예매 가능한 열차 (운행 예정, 예매 오픈, 1시간 이상 뒤 출발) */
    public static Long bookableSchedule(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT schedule_seq FROM schedule
                WHERE status = 'SCHEDULED'
                  AND depplandtime > now() + interval '1 hour'
                  AND (booking_open_at IS NULL OR booking_open_at <= now())
                ORDER BY schedule_seq DESC
                LIMIT 1
                """, Long.class);
    }

    /** 해당 열차·호차의 예약/좌석과 Redis 선점·대기열·멱등키를 지운다 */
    public static void clean(JdbcTemplate jdbc, StringRedisTemplate redis, Long scheduleSeq, int carNo, String... members) {
        jdbc.update("DELETE FROM payment WHERE reservation_seq IN "
                + "(SELECT reservation_seq FROM reservation WHERE schedule_seq = ? AND car_no = ?)", scheduleSeq, carNo);
        jdbc.update("DELETE FROM reservation WHERE schedule_seq = ? AND car_no = ?", scheduleSeq, carNo);
        jdbc.update("DELETE FROM seat WHERE schedule_seq = ? AND car_no = ?", scheduleSeq, carNo);

        deleteKeys(redis, "held:seat:" + scheduleSeq + ":" + carNo + ":*");
        deleteKeys(redis, "held:user:" + scheduleSeq + ":*");
        Set<String> expiry = redis.opsForZSet().range("held:expiry", 0, -1);
        if (expiry != null) {
            expiry.stream().filter(m -> m.startsWith(scheduleSeq + ":" + carNo + ":"))
                    .forEach(m -> redis.opsForZSet().remove("held:expiry", m));
        }
        redis.delete("tago:queue:wait:" + scheduleSeq);
        redis.delete("tago:queue:active:" + scheduleSeq);
        deleteKeys(redis, "tago:queue:user:" + scheduleSeq + ":*");
        for (String member : members) {
            deleteKeys(redis, "idem:payment:" + member + ":*");
        }
    }

    private static void deleteKeys(StringRedisTemplate redis, String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
