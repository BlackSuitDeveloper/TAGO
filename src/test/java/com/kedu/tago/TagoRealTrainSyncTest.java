package com.kedu.tago;

import com.kedu.tago.openapi.TagoApiClient;
import com.kedu.tago.sync.MasterSyncService;
import com.kedu.tago.sync.ScheduleSyncService;
import com.kedu.tago.sync.dto.SyncResultDTO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 실제 TAGO API 로 KTX 마스터/일정/좌석 동기화 테스트 (DB 초기화 후 적재) */
@SpringBootTest
class TagoRealTrainSyncTest {

    @Autowired
    private MasterSyncService masterSyncService;

    @Autowired
    private ScheduleSyncService scheduleSyncService;

    @Autowired
    private TagoApiClient tagoApiClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void syncRealKtxSchedulesAndSeatsFromTagoApi() {
        Assumptions.assumeTrue(
                tagoApiClient.isServiceKeyConfigured(),
                "TAGO_SERVICE_KEY 미설정 시 실제 외부 API 동기화 테스트를 생략합니다."
        );
        // 기존 데이터 및 시퀀스 초기화
        jdbcTemplate.execute("TRUNCATE TABLE seat, schedule, train, station RESTART IDENTITY CASCADE");

        // 마스터(station, train 1~46호기) 적재
        SyncResultDTO masterResult = masterSyncService.syncAllMasterData();
        System.out.println("==========================================");
        System.out.println("[REAL MASTER SYNC] " + masterResult.getMessage());
        System.out.println("==========================================");

        // 해당 날짜 KTX 운행 일정 및 회차별 좌석 적재
        SyncResultDTO scheduleResult = scheduleSyncService.syncSchedulesByDate("20261116");
        System.out.println("==========================================");
        System.out.println("[REAL SCHEDULE & SEAT SYNC] " + scheduleResult.getMessage());
        System.out.println("==========================================");

        Integer stationCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM station", Integer.class);
        Integer trainCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM train", Integer.class);
        Integer minTrainNo = jdbcTemplate.queryForObject("SELECT MIN(trainno) FROM train", Integer.class);
        Integer maxTrainNo = jdbcTemplate.queryForObject("SELECT MAX(trainno) FROM train", Integer.class);
        Integer scheduleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM schedule", Integer.class);
        Integer seatCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seat", Integer.class);
        Long minScheduleSeq = jdbcTemplate.queryForObject("SELECT MIN(schedule_seq) FROM schedule", Long.class);
        Long minSeatSeq = jdbcTemplate.queryForObject("SELECT MIN(seat_seq) FROM seat", Long.class);

        System.out.println("[DB VERIFY] station 총 건수: " + stationCount);
        System.out.println("[DB VERIFY] train 총 건수: " + trainCount + " (호기 범위: " + minTrainNo + " ~ " + maxTrainNo + ")");
        System.out.println("[DB VERIFY] schedule 총 건수: " + scheduleCount + " (시작 schedule_seq: " + minScheduleSeq + ")");
        System.out.println("[DB VERIFY] seat 총 건수: " + seatCount + " (시작 seat_seq: " + minSeatSeq + ")");

        List<Map<String, Object>> lineSummary = jdbcTemplate.queryForList("""
                SELECT line_category, COUNT(*) AS unit_cnt, MIN(trainno) AS min_unit, MAX(trainno) AS max_unit
                FROM train
                GROUP BY line_category
                ORDER BY min_unit
                """);
        for (Map<String, Object> row : lineSummary) {
            System.out.println("[TRAIN 1~46 UNIT SUMMARY] " + row);
        }

        List<Map<String, Object>> scheduleLineSummary = jdbcTemplate.queryForList("""
                SELECT t.line_category, COUNT(*) AS schedule_cnt, MIN(s.trainno) AS min_unit, MAX(s.trainno) AS max_unit
                FROM schedule s
                JOIN train t ON s.trainno = t.trainno
                GROUP BY t.line_category
                ORDER BY min_unit
                """);
        for (Map<String, Object> row : scheduleLineSummary) {
            System.out.println("[SCHEDULE BY LINE SUMMARY] " + row);
        }

        assertEquals(46, trainCount);
        assertEquals(1, minTrainNo);
        assertEquals(46, maxTrainNo);
    }
}
