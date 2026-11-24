package com.kedu.tago;

import com.kedu.tago.sync.ScheduleSyncService;
import com.kedu.tago.sync.dto.SyncResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mock 샘플 데이터 적재 테스트 */
@SpringBootTest
class TagoSyncMockInsertTest {

    @Autowired
    private ScheduleSyncService scheduleSyncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertMockSampleDataIntoDatabase() {
        SyncResultDTO result = scheduleSyncService.insertMockSampleData();
        System.out.println("==========================================");
        System.out.println("[SYNC RESULT] " + result.getMessage());
        System.out.println("==========================================");

        Integer stationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM station", Integer.class);
        Integer trainCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM train", Integer.class);
        Integer scheduleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule", Integer.class);
        Integer seatCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seat", Integer.class);

        System.out.println("[DB VERIFY] station 총 건수: " + stationCount);
        System.out.println("[DB VERIFY] train 총 건수: " + trainCount);
        System.out.println("[DB VERIFY] schedule 총 건수: " + scheduleCount);
        System.out.println("[DB VERIFY] seat 총 건수: " + seatCount);

        List<Map<String, Object>> schedules = jdbcTemplate.queryForList("""
                SELECT s.schedule_seq, s.trainno, t.vehiclekndnm, t.line_category,
                       st1.nodename AS dep_name, st2.nodename AS arr_name,
                       s.depplandtime, s.arrplandtime, s.standard_price,
                       (SELECT COUNT(*) FROM seat se WHERE se.schedule_seq = s.schedule_seq) AS seat_cnt
                FROM schedule s
                JOIN train t ON s.trainno = t.trainno
                JOIN station st1 ON s.depplacename = st1.nodeid
                JOIN station st2 ON s.arrplacename = st2.nodeid
                ORDER BY s.schedule_seq
                """);

        for (Map<String, Object> row : schedules) {
            System.out.println("[SCHEDULE ROW] " + row);
        }

        assertTrue(stationCount != null && stationCount >= 6);
        assertTrue(trainCount != null && trainCount >= 3);
        assertTrue(scheduleCount != null && scheduleCount >= 3);
        assertTrue(seatCount != null && seatCount >= 2805); // 935석 * 3회차
    }
}
