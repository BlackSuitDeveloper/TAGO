package com.kedu.tago.sync;

import com.kedu.tago.openapi.TagoApiClient;
import com.kedu.tago.openapi.TagoRouteConfig;
import com.kedu.tago.openapi.dto.TagoCityDTO;
import com.kedu.tago.openapi.dto.TagoStationDTO;
import com.kedu.tago.openapi.dto.TagoTrainDTO;
import com.kedu.tago.sync.dto.SyncResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 마스터 데이터(station, train) 적재 및 역 이름-ID 캐시 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MasterSyncMapper masterSyncMapper;
    private final TagoApiClient tagoApiClient;
    private final TagoRouteConfig routeConfig;

    // 역 이름 -> 역 ID 캐시
    private final Map<String, String> stationNameToIdCache = new ConcurrentHashMap<>();
    // 역 ID -> 역 이름 캐시
    private final Map<String, String> stationIdToNameCache = new ConcurrentHashMap<>();

    /** station 테이블 비어 있는지 확인 */
    public boolean isStationTableEmpty() {
        return masterSyncMapper.countStations() == 0;
    }

    /** DB 기준으로 역 캐시 재로드 */
    public void refreshStationCacheFromDb() {
        Map<String, String> nameToId = loadStationNameToIdMap();
        Map<String, String> idToName = new LinkedHashMap<>();
        nameToId.forEach((name, id) -> idToName.put(id, name));

        stationNameToIdCache.clear();
        stationNameToIdCache.putAll(nameToId);
        stationIdToNameCache.clear();
        stationIdToNameCache.putAll(idToName);

        log.info("[MasterSync] 역 캐시 로드 완료: 총 {}개 역 캐싱됨", stationNameToIdCache.size());
    }

    /** 역 이름으로 역 ID 조회 */
    public String getNodeIdByStationName(String nodename) {
        if (nodename == null) {
            return null;
        }
        if (stationNameToIdCache.isEmpty()) {
            refreshStationCacheFromDb();
        }
        return stationNameToIdCache.get(nodename.trim());
    }

    /** 역 이름-ID 캐시 복사본 조회 */
    public Map<String, String> getStationNameToIdCache() {
        if (stationNameToIdCache.isEmpty()) {
            refreshStationCacheFromDb();
        }
        return Map.copyOf(stationNameToIdCache);
    }

    /** 역과 열차 마스터 전체 동기화 */
    @Transactional
    public SyncResultDTO syncAllMasterData() {
        int stationCount = syncStationsFromTago();
        refreshStationCacheFromDb();
        int trainCount = syncTrainsFromDesignatedRoutes();

        return SyncResultDTO.builder()
                .syncType("MASTER_SYNC")
                .targetDate(LocalDate.now().format(DATE_FMT))
                .stationCount(stationCount)
                .trainCount(trainCount)
                .executedAt(LocalDateTime.now())
                .message(String.format("마스터 데이터 동기화 완료 (역: %d건, KTX 열차: %d건)", stationCount, trainCount))
                .build();
    }

    /** TAGO API에서 대상 노선의 역 정보 동기화 */
    @Transactional
    public int syncStationsFromTago() {
        if (!tagoApiClient.isServiceKeyConfigured()) {
            log.warn("[MasterSync] TAGO_SERVICE_KEY 미설정으로 station 동기화를 건너뜁니다.");
            return 0;
        }

        List<TagoCityDTO> cities = tagoApiClient.fetchCityCodes();
        if (cities.isEmpty()) {
            log.warn("[MasterSync] TAGO 시/도 목록 조회 결과가 비어 있습니다.");
            return 0;
        }

        java.util.Set<String> targetStationNames = new java.util.HashSet<>();
        for (TagoRouteConfig.RoutePair route : routeConfig.getTargetRoutes()) {
            targetStationNames.add(route.getDepStationName());
            targetStationNames.add(route.getArrStationName());
        }

        Map<String, TagoStationDTO> uniqueByNodeName = new LinkedHashMap<>();
        Map<String, TagoStationDTO> uniqueByNodeId = new LinkedHashMap<>();

        for (TagoCityDTO city : cities) {
            List<TagoStationDTO> cityStations = tagoApiClient.fetchStationsByCity(city.getCitycode(), city.getCityname());
            for (TagoStationDTO st : cityStations) {
                if (targetStationNames.contains(st.getNodename())
                        && st.getNodeid().length() <= 10
                        && !uniqueByNodeName.containsKey(st.getNodename())
                        && !uniqueByNodeId.containsKey(st.getNodeid())) {
                    uniqueByNodeName.put(st.getNodename(), st);
                    uniqueByNodeId.put(st.getNodeid(), st);
                }
            }
        }

        List<TagoStationDTO> stationList = new ArrayList<>(uniqueByNodeId.values());
        int upserted = upsertStations(stationList);
        log.info("[MasterSync] station 테이블 적재 완료: 총 {}건", upserted);
        return upserted;
    }

    /** 지정 노선의 KTX 열차 마스터 동기화 */
    @Transactional
    public int syncTrainsFromDesignatedRoutes() {
        // KTX 1~46호기 고정 마스터 적재
        return upsertKtxTrains(routeConfig.getFixedKtxUnitLineMap());
    }

    /** KTX 열차 마스터 upsert */
    @Transactional
    public int upsertKtxTrains(Map<Integer, String> trainLineMap) {
        int upserted = upsertTrains(trainLineMap);
        if (upserted > 0) {
            log.info("[MasterSync] train 마스터 테이블 Upsert 완료: {}건", upserted);
        }
        return upserted;
    }

    /** 샘플 역 데이터 upsert */
    @Transactional
    public int upsertMockStations(List<TagoStationDTO> stations) {
        return upsertStations(stations);
    }

    // DB 의 역을 이름 -> ID 맵으로 읽기
    private Map<String, String> loadStationNameToIdMap() {
        Map<String, String> nameToId = new LinkedHashMap<>();
        for (TagoStationDTO st : masterSyncMapper.selectAllStations()) {
            if (st.getNodeid() != null && st.getNodename() != null) {
                nameToId.put(st.getNodename().trim(), st.getNodeid().trim());
            }
        }
        return nameToId;
    }

    private int upsertStations(List<TagoStationDTO> stations) {
        if (stations == null || stations.isEmpty()) {
            return 0;
        }
        return masterSyncMapper.upsertStations(stations);
    }

    private int upsertTrains(Map<Integer, String> trainLineMap) {
        if (trainLineMap == null || trainLineMap.isEmpty()) {
            return 0;
        }
        List<TagoTrainDTO> trains = new ArrayList<>(trainLineMap.size());
        trainLineMap.forEach((trainno, lineCategory) -> trains.add(
                TagoTrainDTO.builder().trainno(trainno).vehiclekndnm("KTX").lineCategory(lineCategory).build()));
        return masterSyncMapper.upsertKtxTrains(trains);
    }
}
