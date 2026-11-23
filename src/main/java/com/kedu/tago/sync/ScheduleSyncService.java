package com.kedu.tago.sync;

import com.kedu.tago.openapi.TagoApiClient;
import com.kedu.tago.openapi.TagoRouteConfig;
import com.kedu.tago.openapi.dto.TagoStationDTO;
import com.kedu.tago.openapi.dto.TagoTrainDTO;
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

/**
 * 운행 일정(schedule) 동기화.
 * 외부 API 를 호출해 모은 일정을 한 번의 쿼리로 저장한다(일정마다 존재 확인 + INSERT 하지 않음).
 * 외부 호출 동안 DB 트랜잭션을 열어 두지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_14_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATETIME_12_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // 한 번의 INSERT 에 넣는 일정 수 (쿼리 파라미터 수 제한을 넘지 않도록 나눠서 저장)
    private static final int INSERT_CHUNK_SIZE = 500;

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
        for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
            results.add(syncSchedulesByDate(day.format(DATE_FMT)));
        }
        return results;
    }

    /** 특정 일자(yyyyMMdd)의 운행 일정 동기화 */
    public SyncResultDTO syncSchedulesByDate(String targetDateYmd) {
        if (!tagoApiClient.isServiceKeyConfigured()) {
            return SyncResultDTO.builder()
                    .syncType("SCHEDULE_SYNC")
                    .targetDate(targetDateYmd)
                    .executedAt(LocalDateTime.now())
                    .message("TAGO_SERVICE_KEY가 설정되지 않아 일정 동기화를 수행할 수 없습니다.")
                    .build();
        }

        // 역 ID 캐시가 비어 있으면 필요한 만큼 먼저 채운다
        if (masterSyncService.getStationNameToIdCache().isEmpty()) {
            if (masterSyncService.isStationTableEmpty()) {
                masterSyncService.syncStationsFromTago();
            }
            masterSyncService.refreshStationCacheFromDb();
        }
        masterSyncService.ensureKtxTrains();

        List<ScheduleInsertDTO> candidates = collectSchedules(targetDateYmd);
        int inserted = insertAbsent(candidates);
        int skipped = candidates.size() - inserted;

        String msg = String.format(
                "일자 %s 동기화 완료 (신규 일정: %d건, 기존 스킵: %d건, 생성 좌석: %d석)",
                targetDateYmd, inserted, skipped, 0
        );
        log.info("[ScheduleSync] {}", msg);

        return SyncResultDTO.builder()
                .syncType("SCHEDULE_SYNC")
                .targetDate(targetDateYmd)
                .stationCount(masterSyncService.getStationNameToIdCache().size())
                .trainCount(routeConfig.getFixedKtxUnitLineMap().size())
                .insertedScheduleCount(inserted)
                .skippedScheduleCount(skipped)
                .insertedSeatCount(0)
                .executedAt(LocalDateTime.now())
                .message(msg)
                .build();
    }

    /** 테스트용 KTX 마스터 및 운행 일정 샘플 데이터 적재 */
    @Transactional
    public SyncResultDTO insertMockSampleData() {
        int stationCount = masterSyncService.upsertMockStations(mockStations());
        masterSyncService.refreshStationCacheFromDb();

        Map<Integer, String> mockTrains = new LinkedHashMap<>();
        mockTrains.put(101, "경부선");
        mockTrains.put(102, "경부선");
        mockTrains.put(501, "호남선");
        int trainCount = masterSyncService.upsertKtxTrains(mockTrains);

        List<ScheduleInsertDTO> candidates = new ArrayList<>();
        for (TagoTrainScheduleDTO dto : mockSchedules()) {
            LocalDateTime depTime = parseTagoDateTime(dto.getDepplandtime());
            candidates.add(toInsertDto(dto, depTime, parseTagoDateTime(dto.getArrplandtime()), dto.getAdultcharge()));
        }
        int inserted = insertAbsent(candidates);
        int skipped = candidates.size() - inserted;

        String msg = String.format(
                "임의 샘플 데이터 적재 완료 (역: %d건, 열차: %d건, 신규 일정: %d건, 스킵 일정: %d건, 생성 좌석: %d석)",
                stationCount, trainCount, inserted, skipped, 0
        );
        log.info("[MockSync] {}", msg);

        return SyncResultDTO.builder()
                .syncType("MOCK_SAMPLE_SYNC")
                .targetDate(LocalDate.now().format(DATE_FMT) + "~" + LocalDate.now().plusDays(2).format(DATE_FMT))
                .stationCount(stationCount)
                .trainCount(trainCount)
                .insertedScheduleCount(inserted)
                .skippedScheduleCount(skipped)
                .insertedSeatCount(0)
                .executedAt(LocalDateTime.now())
                .message(msg)
                .build();
    }

    // 대상 노선마다 외부 API 로 일정을 조회해 저장할 일정 목록으로 만든다 (저장은 하지 않음)
    private List<ScheduleInsertDTO> collectSchedules(String targetDateYmd) {
        List<ScheduleInsertDTO> candidates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (TagoRouteConfig.RoutePair route : routeConfig.getTargetRoutes()) {
            String depNodeId = masterSyncService.getNodeIdByStationName(route.getDepStationName());
            String arrNodeId = masterSyncService.getNodeIdByStationName(route.getArrStationName());
            if (depNodeId == null || arrNodeId == null) {
                log.warn("[ScheduleSync] 역 ID 매핑 실패로 노선 건너뜀: {}({}) -> {}({})",
                        route.getDepStationName(), depNodeId, route.getArrStationName(), arrNodeId);
                continue;
            }

            for (TagoTrainScheduleDTO dto : tagoApiClient.fetchKtxSchedules(depNodeId, arrNodeId, targetDateYmd)) {
                LocalDateTime depTime = parseTagoDateTime(dto.getDepplandtime());
                LocalDateTime arrTime = parseTagoDateTime(dto.getArrplandtime());
                if (depTime == null || arrTime == null || !arrTime.isAfter(depTime) || depTime.isBefore(now)) {
                    continue;
                }
                if (dto.getDepPlaceId().equals(dto.getArrPlaceId())) {
                    continue;
                }

                // TAGO 열차번호를 노선에 배정된 KTX 1~46호기로 매핑
                dto.setTrainno(routeConfig.mapToKtxUnitNo(dto.getTrainno(), route.getLineCategory()));
                int price = dto.getAdultcharge() > 0 ? dto.getAdultcharge() : route.getFallbackFare();
                candidates.add(toInsertDto(dto, depTime, arrTime, price));
            }
        }
        return candidates;
    }

    private ScheduleInsertDTO toInsertDto(TagoTrainScheduleDTO dto, LocalDateTime depTime, LocalDateTime arrTime, int price) {
        return ScheduleInsertDTO.builder()
                .trainno(dto.getTrainno())
                .depNodeId(dto.getDepPlaceId())
                .arrNodeId(dto.getArrPlaceId())
                .depTime(depTime)
                .arrTime(arrTime)
                .bookingOpenAt(depTime.minusMonths(1))
                .firstPrice(price)
                .standardPrice(price)
                .build();
    }

    // 같은 일정이 목록 안에 중복되면 하나만 남기고, 이미 DB 에 있는 일정은 제외하며 저장한다. 저장된 건수 반환
    private int insertAbsent(List<ScheduleInsertDTO> candidates) {
        Map<String, ScheduleInsertDTO> unique = new LinkedHashMap<>();
        for (ScheduleInsertDTO c : candidates) {
            unique.putIfAbsent(c.getTrainno() + "|" + c.getDepNodeId() + "|" + c.getArrNodeId() + "|" + c.getDepTime(), c);
        }
        List<ScheduleInsertDTO> list = new ArrayList<>(unique.values());

        int inserted = 0;
        for (int from = 0; from < list.size(); from += INSERT_CHUNK_SIZE) {
            inserted += scheduleSyncMapper.insertSchedulesIfAbsent(list.subList(from, Math.min(from + INSERT_CHUNK_SIZE, list.size())));
        }
        // 목록 안의 중복은 스킵으로 센다
        return Math.min(inserted, candidates.size());
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

    private List<TagoStationDTO> mockStations() {
        return List.of(
                mockStation("NAT010000", "서울", "11", "서울특별시"),
                mockStation("NAT011668", "대전", "25", "대전광역시"),
                mockStation("NAT013271", "동대구", "22", "대구광역시"),
                mockStation("NAT014445", "부산", "21", "부산광역시"),
                mockStation("NAT010032", "용산", "11", "서울특별시"),
                mockStation("NAT031857", "광주송정", "24", "광주광역시")
        );
    }

    private TagoStationDTO mockStation(String nodeId, String nodeName, String cityCode, String cityName) {
        return TagoStationDTO.builder()
                .nodeid(nodeId).nodename(nodeName).citycode(cityCode).cityname(cityName)
                .nodeAddress(routeConfig.resolveStationAddress(nodeName, cityName))
                .build();
    }

    // 오늘부터 3일간 경부선(서울-부산 왕복)과 호남선(용산-광주송정) 샘플 일정
    private List<TagoTrainScheduleDTO> mockSchedules() {
        List<TagoTrainScheduleDTO> schedules = new ArrayList<>();
        for (int dayOffset = 0; dayOffset < 3; dayOffset++) {
            String day = LocalDate.now().plusDays(dayOffset).format(DATE_FMT);
            schedules.add(mockSchedule(101, "NAT010000", "서울", "NAT014445", "부산", day + "090000", day + "113500", 59800));
            schedules.add(mockSchedule(102, "NAT014445", "부산", "NAT010000", "서울", day + "140000", day + "163800", 59800));
            schedules.add(mockSchedule(501, "NAT010032", "용산", "NAT031857", "광주송정", day + "102000", day + "121000", 46800));
        }
        return schedules;
    }

    private TagoTrainScheduleDTO mockSchedule(int trainno, String depId, String depName, String arrId, String arrName,
                                              String depTime, String arrTime, int fare) {
        return TagoTrainScheduleDTO.builder()
                .trainno(trainno).traingradename("KTX")
                .depPlaceId(depId).depplacename(depName)
                .arrPlaceId(arrId).arrplacename(arrName)
                .depplandtime(depTime).arrplandtime(arrTime)
                .adultcharge(fare).build();
    }
}
