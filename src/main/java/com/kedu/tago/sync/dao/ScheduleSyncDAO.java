package com.kedu.tago.sync.dao;

import com.kedu.tago.sync.service.SeatTemplateGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 동적 운행 일정(schedule) 및 회차별 좌석(seat) DB 접근 객체 (DAO).
 */
@Repository
@RequiredArgsConstructor
public class ScheduleSyncDAO {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 동일 열차/출발역/도착역/출발시각 일정 존재 여부 확인 (멱등성 보장)
     */
    public boolean existsSchedule(int trainno, String depNodeId, String arrNodeId, LocalDateTime depTime) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1 FROM schedule
                    WHERE trainno = ?
                      AND depplacename = ?
                      AND arrplacename = ?
                      AND depplandtime = ?
                )
                """;
        Boolean exists = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                trainno,
                depNodeId,
                arrNodeId,
                Timestamp.valueOf(depTime)
        );
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 신규 schedule INSERT 및 생성된 schedule_seq 반환
     */
    public Long insertScheduleAndReturnSeq(
            int trainno,
            String depNodeId,
            String arrNodeId,
            LocalDateTime depTime,
            LocalDateTime arrTime,
            LocalDateTime bookingOpenAt,
            int firstPrice,
            int standardPrice) {

        String sql = """
                INSERT INTO schedule (
                    trainno, depplacename, arrplacename,
                    depplandtime, arrplandtime, booking_open_at,
                    first_price, standard_price, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
                RETURNING schedule_seq
                """;

        return jdbcTemplate.queryForObject(
                sql,
                Long.class,
                trainno,
                depNodeId,
                arrNodeId,
                Timestamp.valueOf(depTime),
                Timestamp.valueOf(arrTime),
                Timestamp.valueOf(bookingOpenAt),
                firstPrice,
                standardPrice
        );
    }

    /**
     * 신규 생성된 schedule_seq에 대해 1~18호차 총 935석(모두 STANDARD) 좌석을 batchUpdate로 일괄 적재
     */
    public int batchInsertSeats(long scheduleSeq, List<SeatTemplateGenerator.SeatTemplateItem> template) {
        String sql = """
                INSERT INTO seat (schedule_seq, car_no, seat_no, seat_grade, status, version)
                VALUES (?, ?, ?, ?, 'AVAILABLE', 0)
                ON CONFLICT (schedule_seq, car_no, seat_no) DO NOTHING
                """;

        jdbcTemplate.batchUpdate(sql, template, template.size(), (ps, seat) -> {
            ps.setLong(1, scheduleSeq);
            ps.setInt(2, seat.getCarNo());
            ps.setString(3, seat.getSeatNo());
            ps.setString(4, seat.getSeatGrade());
        });

        return template.size();
    }
}
