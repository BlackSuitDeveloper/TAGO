package com.kedu.tago.sync.service;

import com.kedu.tago.sync.client.TagoApiClient;
import com.kedu.tago.sync.config.TagoRouteConfig;
import com.kedu.tago.sync.dao.MasterSyncDAO;
import com.kedu.tago.sync.dto.SyncResultDTO;
import com.kedu.tago.sync.dto.TagoCityDTO;
import com.kedu.tago.sync.dto.TagoStationDTO;
import com.kedu.tago.sync.dto.TagoTrainScheduleDTO;
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

/**
 * 정적 마스터 데이터(station, train) 적재 및 역 정보(nodename <-> nodeid) 인메모리 캐시 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MasterSyncDAO masterSyncDAO;
    private final TagoApiClient tagoApiClient;
    private final TagoRouteConfig routeConfig;

    // nodename -> nodeid 캐시 (schedule 적재 시 FK 매핑용)
    private final Map<String, String> stationNameToIdCache = new ConcurrentHashMap<>();
    // nodeid -> nodename 캐시
    private final Map<String, String> stationIdToNameCache = new ConcurrentHashMap<>();

    public boolean isStationTableEmpty() {
        return masterSyncDAO.countStations() == 0;
    }

    public void refreshStationCacheFromDb() {
        Map<String, String> nameToId = masterSyncDAO.findAllStationNameToIdMap();
        Map<String, String> idToName = new LinkedHashMap<>();
        nameToId.forEach((name, id) -> idToName.put(id, name));

        stationNameToIdCache.clear();
        stationNameToIdCache.putAll(nameToId);
        stationIdToNameCache.clear();
        stationIdToNameCache.putAll(idToName);

        log.info("[MasterSync] 역 캐시 로드 완료: 총 {}개 역 캐싱됨", stationNameToIdCache.size());
    }

    public String getNodeIdByStationName(String nodename) {
        if (nodename == null) {
            return null;
        }
        if (stationNameToIdCache.isEmpty()) {
            refreshStationCacheFromDb();
        }
        return stationNameToIdCache.get(nodename.trim());
    }

    public Map<String, String> getStationNameToIdCache() {
        if (stationNameToIdCache.isEmpty()) {
            refreshStationCacheFromDb();
        }
        return Map.copyOf(stationNameToIdCache);
    }

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

        Map<String, TagoStationDTO> uniqueByNodeName = new LinkedHashMap<>();
        Map<String, TagoStationDTO> uniqueByNodeId = new LinkedHashMap<>();

        for (TagoCityDTO city : cities) {
            List<TagoStationDTO> cityStations = tagoApiClient.fetchStationsByCity(city.getCitycode(), city.getCityname());
            for (TagoStationDTO st : cityStations) {
                if (st.getNodeid().length() <= 10
                        && !uniqueByNodeName.containsKey(st.getNodename())
                        && !uniqueByNodeId.containsKey(st.getNodeid())) {
                    uniqueByNodeName.put(st.getNodename(), st);
                    uniqueByNodeId.put(st.getNodeid(), st);
                }
            }
        }

        List<TagoStationDTO> stationList = new ArrayList<>(uniqueByNodeId.values());
        int upserted = masterSyncDAO.batchUpsertStations(stationList);
        log.info("[MasterSync] station 테이블 적재 완료: 총 {}건", upserted);
        return upserted;
    }

    @Transactional
    public int syncTrainsFromDesignatedRoutes() {
        // KTX 1세대 1호기 ~ 46호기(trainno 1 ~ 46) 고정 마스터 적재
        return upsertKtxTrains(routeConfig.getFixedKtxUnitLineMap());
    }

    @Transactional
    public int upsertKtxTrains(Map<Integer, String> trainLineMap) {
        int upserted = masterSyncDAO.batchUpsertKtxTrains(trainLineMap);
        if (upserted > 0) {
            log.info("[MasterSync] train 마스터 테이블 Upsert 완료: {}건", upserted);
        }
        return upserted;
    }

    @Transactional
    public int upsertMockStations(List<TagoStationDTO> stations) {
        return masterSyncDAO.batchUpsertStations(stations);
    }
}
