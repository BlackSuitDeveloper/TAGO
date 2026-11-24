package com.kedu.tago.sync;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.common.security.RequireRole;
import com.kedu.tago.member.Role;
import com.kedu.tago.openapi.TagoRouteConfig;
import com.kedu.tago.seat.SeatTemplateGenerator;
import com.kedu.tago.sync.dto.SyncResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 공공데이터(TAGO) 역·시간표 동기화 관리자 API */
@RequireRole(Role.ADMIN)
@RestController
@RequestMapping(ApiPaths.V1 + "/admin/sync")
@RequiredArgsConstructor
public class SyncController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MasterSyncService masterSyncService;
    private final ScheduleSyncService scheduleSyncService;
    private final TagoRouteConfig routeConfig;

    /** 역·열차 마스터 데이터 동기화 (POST /api/v1/admin/sync/master) */
    @PostMapping("/master")
    public ResWrapper<SyncResultDTO> syncMasterData() {
        return ResWrapper.ok(masterSyncService.syncAllMasterData());
    }

    /** 목업 샘플 데이터 적재 (POST /api/v1/admin/sync/mock) */
    @PostMapping("/mock")
    public ResWrapper<SyncResultDTO> syncMockSampleData() {
        return ResWrapper.ok(scheduleSyncService.insertMockSampleData());
    }

    /** 특정 날짜 시간표 동기화 (POST /api/v1/admin/sync/schedules) */
    @PostMapping("/schedules")
    public ResWrapper<SyncResultDTO> syncSchedulesByDate(
            @RequestParam(value = "date", required = false) String date) {

        String targetDate = (date == null || date.isBlank())
                ? LocalDate.now().format(DATE_FMT)
                : date.replaceAll("[^0-9]", "");

        return ResWrapper.ok(scheduleSyncService.syncSchedulesByDate(targetDate));
    }

    /** 기간별 시간표 동기화 (POST /api/v1/admin/sync/schedules/range) */
    @PostMapping("/schedules/range")
    public ResWrapper<List<SyncResultDTO>> syncSchedulesByRange(
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : start.plusDays(2); // 기본 3일치

        return ResWrapper.list(scheduleSyncService.syncSchedulesByDateRange(start, end));
    }

    /** 동기화 설정 상태 조회 (GET /api/v1/admin/sync/status) */
    @GetMapping("/status")
    public ResWrapper<Map<String, Object>> getSyncStatus() {
        Map<String, String> cachedStations = masterSyncService.getStationNameToIdCache();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cachedStationCount", cachedStations.size());
        body.put("designatedRouteCount", routeConfig.getTargetRoutes().size());
        body.put("designatedRoutes", routeConfig.getTargetRoutes());
        body.put("ktxSeatTemplate", Map.of(
                "totalCars", 18,
                "seatGrade", SeatTemplateGenerator.GRADE_STANDARD,
                "totalSeatsPerSchedule", SeatTemplateGenerator.TOTAL_KTX_SEATS
        ));
        return ResWrapper.ok(body);
    }
}
