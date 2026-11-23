package com.kedu.tago.sync;

import com.kedu.tago.openapi.TagoApiClient;
import com.kedu.tago.openapi.TagoRouteConfig;
import com.kedu.tago.openapi.dto.TagoStationDTO;
import com.kedu.tago.openapi.dto.TagoTrainScheduleDTO;
import com.kedu.tago.sync.dto.ScheduleInsertDTO;
import com.kedu.tago.sync.dto.SyncResultDTO;
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

/** 운행 일정(schedule) 동기화 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATETIME_12_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ScheduleSyncMapper scheduleSyncMapper;
    private final TagoApiClient tagoApiClient;
    private final TagoRouteConfig routeConfig;
    private final MasterSyncService masterSyncService;

    @Value("${tago.sync.sliding-window-days:3}")
    private int slidingWindowDays;

    /** 매일 새벽 3시 슬라이딩 윈도우 기준 일정 동기화 */
    @Scheduled(cron = "${tago.sync.cron:0 0 3 * * *}")
    public void scheduledDailySlidingWindowSync() {
        String targetDate = LocalDate.now().plusDays(slidingWindowDays).format(DATE_FMT);
        log.info("[ScheduleSync] 일간 슬라이딩 윈도우 배치 시작 - 대상 일자: {} (+{}일)", targetDate, slidingWindowDays);
        SyncResultDTO result = syncSchedulesByDate(targetDate);
        log.info("[ScheduleSync] 일간 슬라이딩 윈도우 배치 완료 - {}", result.getMessage());
    }

    /** 기간 내 일자별 운행 일정 동기화 */
    public List<SyncResultDTO> syncSchedulesByDateRange(LocalDate startDate, LocalDate endDate) {
        List<SyncResultDTO> results = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            results.add(syncSchedulesByDate(cursor.format(DATE_FMT)));
            cursor = cursor.plusDays(1);
        }
        return results;
    }

    /** 특정 일자(yyyyMMdd)의 운행 일정 동기화 */
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
                // TAGO 열차번호를 노선에 배정된 KTX 1~46호기로 매핑
                int mappedUnitNo = routeConfig.mapToKtxUnitNo(dto.getTrainno(), route.getLineCategory());
                dto.setTrainno(mappedUnitNo);
                collectedSchedules.add(dto);
            }
        }

        int upsertedTrainCount = masterSyncService.upsertKtxTrains(routeConfig.getFixedKtxUnitLineMap());

        int insertedScheduleCount = 0;
        int skippedScheduleCount = 0;
        int insertedSeatCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (TagoTrainScheduleDTO dto : collectedSchedules) {
            LocalDateTime depTime = parseTagoDateTime(dto.getDepplandtime());
            LocalDateTime arrTime = parseTagoDateTime(dto.getArrplandtime());

            if (depTime == null || arrTime == null || !arrTime.isAfter(depTime)) {
                continue;
            }
            if (depTime.isBefore(now)) {
                continue;
            }
            if (dto.getDepPlaceId().equals(dto.getArrPlaceId())) {
                continue;
            }

            if (scheduleSyncMapper.existsSchedule(dto.getTrainno(), dto.getDepPlaceId(), dto.getArrPlaceId(), depTime)) {
                skippedScheduleCount++;
                continue;
            }

            int fallbackFare = routeFallbackFareMap.getOrDefault(dto.getDepPlaceId() + "->" + dto.getArrPlaceId(), 30000);
            int unifiedPrice = dto.getAdultcharge() > 0 ? dto.getAdultcharge() : fallbackFare;
            LocalDateTime bookingOpenAt = depTime.minusMonths(1);

            Long scheduleSeq = insertSchedule(
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

    /** 테스트용 KTX 마스터 및 운행 일정 샘플 데이터 적재 */
    @Transactional
    public SyncResultDTO insertMockSampleData() {
        List<TagoStationDTO> mockStations = List.of(
                TagoStationDTO.builder()
                        .nodeid("NAT010000").nodename("서울").citycode("11").cityname("서울특별시")
                        .nodeAddress(routeConfig.resolveStationAddress("서울", "서울특별시")).build(),
                TagoStationDTO.builder()
                        .nodeid("NAT011668").nodename("대전").citycode("25").cityname("대전광역시")
                        .nodeAddress(routeConfig.resolveStationAddress("대전", "대전광역시")).build(),
                TagoStationDTO.builder()
                        .nodeid("NAT013271").nodename("동대구").citycode("22").cityname("대구광역시")
                        .nodeAddress(routeConfig.resolveStationAddress("동대구", "대구광역시")).build(),
                TagoStationDTO.builder()
                        .nodeid("NAT014445").nodename("부산").citycode("21").cityname("부산광역시")
                        .nodeAddress(routeConfig.resolveStationAddress("부산", "부산광역시")).build(),
                TagoStationDTO.builder()
                        .nodeid("NAT010032").nodename("용산").citycode("11").cityname("서울특별시")
                        .nodeAddress(routeConfig.resolveStationAddress("용산", "서울특별시")).build(),
                TagoStationDTO.builder()
                        .nodeid("NAT031857").nodename("광주송정").citycode("24").cityname("광주광역시")
                        .nodeAddress(routeConfig.resolveStationAddress("광주송정", "광주광역시")).build()
        );

        int stationCount = masterSyncService.upsertMockStations(mockStations);
        masterSyncService.refreshStationCacheFromDb();

        Map<Integer, String> mockTrains = new LinkedHashMap<>();
        mockTrains.put(101, "경부선");
        mockTrains.put(102, "경부선");
        mockTrains.put(501, "호남선");
        int trainCount = masterSyncService.upsertKtxTrains(mockTrains);

        List<TagoTrainScheduleDTO> mockSchedules = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
            String targetDateYmd = LocalDate.now().plusDays(dayOffset).format(DATE_FMT);
            mockSchedules.add(TagoTrainScheduleDTO.builder()
                    .trainno(101).traingradename("KTX")
                    .depPlaceId("NAT010000").depplacename("서울")
                    .arrPlaceId("NAT014445").arrplacename("부산")
                    .depplandtime(targetDateYmd + "090000")
                    .arrplandtime(targetDateYmd + "113500")
                    .adultcharge(59800).build());
            mockSchedules.add(TagoTrainScheduleDTO.builder()
                    .trainno(102).traingradename("KTX")
                    .depPlaceId("NAT014445").depplacename("부산")
                    .arrPlaceId("NAT010000").arrplacename("서울")
                    .depplandtime(targetDateYmd + "140000")
                    .arrplandtime(targetDateYmd + "163800")
                    .adultcharge(59800).build());
            mockSchedules.add(TagoTrainScheduleDTO.builder()
                    .trainno(501).traingradename("KTX")
                    .depPlaceId("NAT010032").depplacename("용산")
                    .arrPlaceId("NAT031857").arrplacename("광주송정")
                    .depplandtime(targetDateYmd + "102000")
                    .arrplandtime(targetDateYmd + "121000")
                    .adultcharge(46800).build());
        }

        int insertedScheduleCount = 0;
        int skippedScheduleCount = 0;
        int insertedSeatCount = 0;

        for (TagoTrainScheduleDTO dto : mockSchedules) {
            LocalDateTime depTime = parseTagoDateTime(dto.getDepplandtime());
            LocalDateTime arrTime = parseTagoDateTime(dto.getArrplandtime());
            if (scheduleSyncMapper.existsSchedule(dto.getTrainno(), dto.getDepPlaceId(), dto.getArrPlaceId(), depTime)) {
                skippedScheduleCount++;
                continue;
            }
            LocalDateTime bookingOpenAt = depTime.minusMonths(1);
            Long scheduleSeq = insertSchedule(
                    dto.getTrainno(),
                    dto.getDepPlaceId(),
                    dto.getArrPlaceId(),
                    depTime,
                    arrTime,
                    bookingOpenAt,
                    dto.getAdultcharge(),
                    dto.getAdultcharge()
            );
            if (scheduleSeq != null) {
                insertedScheduleCount++;
            }
        }

        String msg = String.format(
                "임의 샘플 데이터 적재 완료 (역: %d건, 열차: %d건, 신규 일정: %d건, 스킵 일정: %d건, 생성 좌석: %d석)",
                stationCount, trainCount, insertedScheduleCount, skippedScheduleCount, insertedSeatCount
        );
        log.info("[MockSync] {}", msg);

        return SyncResultDTO.builder()
                .syncType("MOCK_SAMPLE_SYNC")
                .targetDate(LocalDate.now().format(DATE_FMT) + "~" + LocalDate.now().plusDays(2).format(DATE_FMT))
                .stationCount(stationCount)
                .trainCount(trainCount)
                .insertedScheduleCount(insertedScheduleCount)
                .skippedScheduleCount(skippedScheduleCount)
                .insertedSeatCount(insertedSeatCount)
                .executedAt(LocalDateTime.now())
                .message(msg)
                .build();
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

    // 일정 1건 저장 후 생성된 schedule_seq 반환
    private Long insertSchedule(int trainno, String depNodeId, String arrNodeId, LocalDateTime depTime, LocalDateTime arrTime,
                                LocalDateTime bookingOpenAt, int firstPrice, int standardPrice) {
        ScheduleInsertDTO schedule = ScheduleInsertDTO.builder()
                .trainno(trainno)
                .depNodeId(depNodeId)
                .arrNodeId(arrNodeId)
                .depTime(depTime)
                .arrTime(arrTime)
                .bookingOpenAt(bookingOpenAt)
                .firstPrice(firstPrice)
                .standardPrice(standardPrice)
                .build();
        scheduleSyncMapper.insertSchedule(schedule);
        return schedule.getScheduleSeq();
    }
}
