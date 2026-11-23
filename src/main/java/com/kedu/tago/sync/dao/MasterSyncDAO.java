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

/** 마스터 데이터(station, train) DAO (SQL: mapper/master_sync_mapper.xml) */
@Repository
@RequiredArgsConstructor
public class MasterSyncDAO {

    private final SqlSession sqlSession;

    /** 역 전체 건수 */
    public int countStations() {
        Integer count = sqlSession.selectOne("MasterSync.countStations");
        return count == null ? 0 : count;
    }

    /** 역 전체 조회 (nodename -> nodeid 매핑) */
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

    /** 역 일괄 Upsert */
    public int batchUpsertStations(List<TagoStationDTO> stationList) {
        if (stationList == null || stationList.isEmpty()) {
            return 0;
        }
        sqlSession.insert("MasterSync.upsertStations", stationList);
        return stationList.size();
    }

    /** KTX 열차 마스터 일괄 Upsert (vehiclekndnm = 'KTX' 고정) */
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
