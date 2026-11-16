package com.kedu.tago.sync.controller;

import com.kedu.tago.sync.config.TagoRouteConfig;
import com.kedu.tago.sync.dto.SyncResultDTO;
import com.kedu.tago.sync.service.MasterSyncService;
import com.kedu.tago.sync.service.ScheduleSyncService;
import com.kedu.tago.sync.service.SeatTemplateGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/admin/sync")
@RequiredArgsConstructor
public class TagoSyncController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MasterSyncService masterSyncService;
    private final ScheduleSyncService scheduleSyncService;
    private final TagoRouteConfig routeConfig;

    @PostMapping("/master")
    public ResponseEntity<SyncResultDTO> syncMasterData() {
        SyncResultDTO result = masterSyncService.syncAllMasterData();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/schedules")
    public ResponseEntity<SyncResultDTO> syncSchedulesByDate(
            @RequestParam(value = "date", required = false) String date) {
        String targetDate = (date == null || date.isBlank())
                ? LocalDate.now().format(DATE_FMT)
                : date.replaceAll("[^0-9]", "");
        SyncResultDTO result = scheduleSyncService.syncSchedulesByDate(targetDate);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/schedules/range")
    public ResponseEntity<List<SyncResultDTO>> syncSchedulesByRange(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<SyncResultDTO> results = scheduleSyncService.syncSchedulesByDateRange(startDate, endDate);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
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
        return ResponseEntity.ok(body);
    }
}
