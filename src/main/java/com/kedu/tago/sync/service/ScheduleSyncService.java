package com.kedu.tago.sync.service;

import com.kedu.tago.sync.client.TagoApiClient;
import com.kedu.tago.sync.config.TagoRouteConfig;
import com.kedu.tago.sync.dao.ScheduleSyncDAO;
import com.kedu.tago.sync.dto.SyncResultDTO;
import com.kedu.tago.sync.dto.TagoTrainScheduleDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 동적 운행 일정(schedule) 및 회차별 좌석(seat, 1~18호차 935석) 동기화 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATETIME_12_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ScheduleSyncDAO scheduleSyncDAO;
    private final TagoApiClient tagoApiClient;
    private final TagoRouteConfig routeConfig;
    private final MasterSyncService masterSyncService;
    private final SeatTemplateGenerator seatTemplateGenerator;

    @Value("${tago.sync.sliding-window-days:14}")
    private int slidingWindowDays;

    @Scheduled(cron = "${tago.sync.cron:0 0 3 * * *}")
    public void scheduledDailySlidingWindowSync() {
        String targetDate = LocalDate.now().plusDays(slidingWindowDays).format(DATE_FMT);
        log.info("[ScheduleSync] 일간 슬라이딩 윈도우 배치 시작 - 대상 일자: {} (+{}일)", targetDate, slidingWindowDays);
        SyncResultDTO result = syncSchedulesByDate(targetDate);
        log.info("[ScheduleSync] 일간 슬라이딩 윈도우 배치 완료 - {}", result.getMessage());
    }

    public List<SyncResultDTO> syncSchedulesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<SyncResultDTO> results = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            results.add(syncSchedulesByDate(cursor.format(DATE_FMT)));
            cursor = cursor.plusDays(1);
        }
        return results;
    }

    @Transactional
    public SyncResultDTO syncSchedulesByDate(String targetDateYmd) {
        if (!tagoApiClient.isServiceKeyConfigured()) {
            return SyncResultDTO.builder()
                    .syncType("SCHEDULE_SYNC")
                    .targetDate(targetDateYmd)
                    .executedAt(LocalDateTime.now())
                    .message("TAGO_SERVICE_KEY가 설정되지 않아 일정 동기화를 수행할 수 없습니다.")
                    .build();
        }

        if (masterSyncService.getStationNameToIdCache().isEmpty()) {
            if (masterSyncService.isStationTableEmpty()) {
                masterSyncService.syncStationsFromTago();
            }
            masterSyncService.refreshStationCacheFromDb();
        }

        List<TagoTrainScheduleDTO> collectedSchedules = new ArrayList<>();
        Map<Integer, String> discoveredTrains = new LinkedHashMap<>();
        Map<String, Integer> routeFallbackFareMap = new LinkedHashMap<>();

        for (TagoRouteConfig.RoutePair route : routeConfig.getTargetRoutes()) {
            String depNodeId = masterSyncService.getNodeIdByStationName(route.getDepStationName());
            String arrNodeId = masterSyncService.getNodeIdByStationName(route.getArrStationName());

            if (depNodeId == null || arrNodeId == null) {
                log.warn("[ScheduleSync] 역 ID 매핑 실패로 노선 건너뜀: {}({}) -> {}({})",
                        route.getDepStationName(), depNodeId, route.getArrStationName(), arrNodeId);
                continue;
            }

            routeFallbackFareMap.put(depNodeId + "->" + arrNodeId, route.getFallbackFare());

            List<TagoTrainScheduleDTO> apiSchedules = tagoApiClient.fetchKtxSchedules(depNodeId, arrNodeId, targetDateYmd);
            for (TagoTrainScheduleDTO dto : apiSchedules) {
                discoveredTrains.putIfAbsent(dto.getTrainno(), route.getLineCategory());
                collectedSchedules.add(dto);
            }
        }

        int upsertedTrainCount = masterSyncService.upsertKtxTrains(discoveredTrains);

        int insertedScheduleCount = 0;
        int skippedScheduleCount = 0;
        int insertedSeatCount = 0;

        for (TagoTrainScheduleDTO dto : collectedSchedules) {
            LocalDateTime depTime = parseTagoDateTime(dto.getDepplandtime());
            LocalDateTime arrTime = parseTagoDateTime(dto.getArrplandtime());

            if (depTime == null || arrTime == null || !arrTime.isAfter(depTime)) {
                continue;
            }
            if (dto.getDepPlaceId().equals(dto.getArrPlaceId())) {
                continue;
            }

            if (scheduleSyncDAO.existsSchedule(dto.getTrainno(), dto.getDepPlaceId(), dto.getArrPlaceId(), depTime)) {
                skippedScheduleCount++;
                continue;
            }

            int fallbackFare = routeFallbackFareMap.getOrDefault(dto.getDepPlaceId() + "->" + dto.getArrPlaceId(), 30000);
            int unifiedPrice = dto.getAdultcharge() > 0 ? dto.getAdultcharge() : fallbackFare;
            LocalDateTime bookingOpenAt = depTime.minusMonths(1);

            Long scheduleSeq = scheduleSyncDAO.insertScheduleAndReturnSeq(
                    dto.getTrainno(),
                    dto.getDepPlaceId(),
                    dto.getArrPlaceId(),
                    depTime,
                    arrTime,
                    bookingOpenAt,
                    unifiedPrice,
                    unifiedPrice
            );

            if (scheduleSeq != null) {
                insertedScheduleCount++;
                insertedSeatCount += batchInsertSeatsForSchedule(scheduleSeq);
            }
        }

        String msg = String.format(
                "일자 %s 동기화 완료 (신규 일정: %d건, 기존 스킵: %d건, 생성 좌석: %d석)",
                targetDateYmd, insertedScheduleCount, skippedScheduleCount, insertedSeatCount
        );
        log.info("[ScheduleSync] {}", msg);

        return SyncResultDTO.builder()
                .syncType("SCHEDULE_SYNC")
                .targetDate(targetDateYmd)
                .stationCount(masterSyncService.getStationNameToIdCache().size())
                .trainCount(upsertedTrainCount)
                .insertedScheduleCount(insertedScheduleCount)
                .skippedScheduleCount(skippedScheduleCount)
                .insertedSeatCount(insertedSeatCount)
                .executedAt(LocalDateTime.now())
                .message(msg)
                .build();
    }

    public int batchInsertSeatsForSchedule(long scheduleSeq) {
        return scheduleSyncDAO.batchInsertSeats(scheduleSeq, seatTemplateGenerator.getKtxSeatTemplate());
    }

    private LocalDateTime parseTagoDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.length() == 14) {
                return LocalDateTime.parse(trimmed, DATETIME_14_FMT);
            } else if (trimmed.length() == 12) {
                return LocalDateTime.parse(trimmed, DATETIME_12_FMT);
            }
        } catch (Exception e) {
            log.warn("[ScheduleSync] 날짜 파싱 실패: {}", raw);
        }
        return null;
    }
}
