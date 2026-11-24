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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 마스터 데이터(station, train) 적재와 역 이름-ID 캐시.
 * 외부 API 를 호출하는 메서드는 DB 트랜잭션으로 감싸지 않는다(네트워크 대기 동안 DB 커넥션을 붙잡지 않기 위해).
 * DB 쓰기는 모두 한 번의 쿼리라 그 자체로 원자적이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterSyncService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MasterSyncMapper masterSyncMapper;
    private final TagoApiClient tagoApiClient;
    private final TagoRouteConfig routeConfig;

    // 역 이름 -> 역 ID 캐시. 갱신할 때 새 맵으로 통째로 교체해서, 읽는 쪽이 중간에 빈 맵을 보지 않게 한다
    private volatile Map<String, String> stationNameToIdCache = Map.of();

    // KTX 열차 마스터를 이 프로세스에서 이미 적재했는지 (일자마다 반복 upsert 하지 않기 위함)
    private final AtomicBoolean ktxTrainsReady = new AtomicBoolean(false);

    /** station 테이블이 비어 있는지 확인 */
    public boolean isStationTableEmpty() {
        return masterSyncMapper.countStations() == 0;
    }

    /** DB 기준으로 역 캐시 재로드 */
    public void refreshStationCacheFromDb() {
        Map<String, String> nameToId = new LinkedHashMap<>();
        for (TagoStationDTO st : masterSyncMapper.selectAllStations()) {
            if (st.getNodeid() != null && st.getNodename() != null) {
                nameToId.put(st.getNodename().trim(), st.getNodeid().trim());
            }
        }
        stationNameToIdCache = Map.copyOf(nameToId);
        log.info("[MasterSync] 역 캐시 로드 완료: 총 {}개 역 캐싱됨", nameToId.size());
    }

    /** 역 이름으로 역 ID 조회 */
    public String getNodeIdByStationName(String nodename) {
        if (nodename == null) {
            return null;
        }
        return getStationNameToIdCache().get(nodename.trim());
    }

    /** 역 이름-ID 캐시 조회 (변경 불가능한 맵) */
    public Map<String, String> getStationNameToIdCache() {
        if (stationNameToIdCache.isEmpty()) {
            refreshStationCacheFromDb();
        }
        return stationNameToIdCache;
    }

    /** 역과 열차 마스터 전체 동기화 */
    public SyncResultDTO syncAllMasterData() {
        int stationCount = syncStationsFromTago();
        refreshStationCacheFromDb();
        int trainCount = upsertKtxTrains(routeConfig.getFixedKtxUnitLineMap());
        return SyncResultDTO.builder()
                .syncType("MASTER_SYNC")
                .targetDate(LocalDate.now().format(DATE_FMT))
                .stationCount(stationCount)
                .trainCount(trainCount)
                .executedAt(LocalDateTime.now())
                .message(String.format("마스터 데이터 동기화 완료 (역: %d건, KTX 열차: %d건)", stationCount, trainCount))
                .build();
    }

    /** TAGO API 에서 대상 노선의 역 정보 동기화 */
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

        Set<String> targetStationNames = new HashSet<>();
        for (TagoRouteConfig.RoutePair route : routeConfig.getTargetRoutes()) {
            targetStationNames.add(route.getDepStationName());
            targetStationNames.add(route.getArrStationName());
        }

        Map<String, TagoStationDTO> uniqueByNodeName = new LinkedHashMap<>();
        Map<String, TagoStationDTO> uniqueByNodeId = new LinkedHashMap<>();
        for (TagoCityDTO city : cities) {
            for (TagoStationDTO st : tagoApiClient.fetchStationsByCity(city.getCitycode(), city.getCityname())) {
                if (targetStationNames.contains(st.getNodename())
                        && st.getNodeid().length() <= 10
                        && !uniqueByNodeName.containsKey(st.getNodename())
                        && !uniqueByNodeId.containsKey(st.getNodeid())) {
                    uniqueByNodeName.put(st.getNodename(), st);
                    uniqueByNodeId.put(st.getNodeid(), st);
                }
            }
        }

        int upserted = upsertStations(new ArrayList<>(uniqueByNodeId.values()));
        log.info("[MasterSync] station 테이블 적재 완료: 총 {}건", upserted);
        return upserted;
    }

    /** KTX 열차 마스터 upsert */
    public int upsertKtxTrains(Map<Integer, String> trainLineMap) {
        if (trainLineMap == null || trainLineMap.isEmpty()) {
            return 0;
        }
        List<TagoTrainDTO> trains = new ArrayList<>(trainLineMap.size());
        trainLineMap.forEach((trainno, lineCategory) -> trains.add(
                TagoTrainDTO.builder().trainno(trainno).vehiclekndnm("KTX").lineCategory(lineCategory).build()));
        int upserted = masterSyncMapper.upsertKtxTrains(trains);
        if (upserted > 0) {
            log.info("[MasterSync] train 마스터 테이블 Upsert 완료: {}건", upserted);
        }
        return upserted;
    }

    /** 고정 KTX 열차 마스터가 적재돼 있도록 보장. 프로세스당 한 번만 upsert 한다 */
    public void ensureKtxTrains() {
        if (ktxTrainsReady.compareAndSet(false, true)) {
            try {
                upsertKtxTrains(routeConfig.getFixedKtxUnitLineMap());
            } catch (RuntimeException e) {
                ktxTrainsReady.set(false);
                throw e;
            }
        }
    }

    /** 샘플 역 데이터 upsert */
    public int upsertMockStations(List<TagoStationDTO> stations) {
        return upsertStations(stations);
    }

    private int upsertStations(List<TagoStationDTO> stations) {
        if (stations == null || stations.isEmpty()) {
            return 0;
        }
        return masterSyncMapper.upsertStations(stations);
    }
}
