package com.kedu.tago.sync.dao;

import com.kedu.tago.sync.dto.TagoStationDTO;
import com.kedu.tago.sync.dto.TagoTrainDTO;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정적 마스터 데이터(station, train) MyBatis 기반 DB 접근 객체 (DAO).
 * SQL문은 src/main/resources/mapper/master_sync_mapper.xml 에 정의되어 있습니다.
 */
@Repository
@RequiredArgsConstructor
public class MasterSyncDAO {

    private final SqlSession sqlSession;

    public int countStations() {
        Integer count = sqlSession.selectOne("MasterSync.countStations");
        return count == null ? 0 : count;
    }

    /**
     * station 테이블 전체 조회 (nodename -> nodeid 매핑 반환)
     */
    public Map<String, String> findAllStationNameToIdMap() {
        List<TagoStationDTO> stations = sqlSession.selectList("MasterSync.selectAllStations");
        Map<String, String> nameToId = new LinkedHashMap<>();
        for (TagoStationDTO st : stations) {
            if (st.getNodeid() != null && st.getNodename() != null) {
                nameToId.put(st.getNodename().trim(), st.getNodeid().trim());
            }
        }
        return nameToId;
    }

    /**
     * station 테이블 일괄 Upsert (MyBatis <foreach> 다중 VALUES)
     */
    public int batchUpsertStations(List<TagoStationDTO> stationList) {
        if (stationList == null || stationList.isEmpty()) {
            return 0;
        }
        sqlSession.insert("MasterSync.upsertStations", stationList);
        return stationList.size();
    }

    /**
     * KTX 열차 마스터(train) 일괄 Upsert (vehiclekndnm = 'KTX' 고정)
     */
    public int batchUpsertKtxTrains(Map<Integer, String> trainLineMap) {
        if (trainLineMap == null || trainLineMap.isEmpty()) {
            return 0;
        }
        List<TagoTrainDTO> trainList = new ArrayList<>(trainLineMap.size());
        trainLineMap.forEach((trainno, lineCategory) ->
                trainList.add(TagoTrainDTO.builder()
                        .trainno(trainno)
                        .vehiclekndnm("KTX")
                        .lineCategory(lineCategory)
                        .build())
        );
        sqlSession.insert("MasterSync.upsertKtxTrains", trainList);
        return trainList.size();
    }
}
