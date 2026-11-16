package com.kedu.tago.sync.dao;

import com.kedu.tago.sync.dto.TagoStationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 정적 마스터 데이터(station, train) DB 접근 객체 (DAO).
 */
@Repository
@RequiredArgsConstructor
public class MasterSyncDAO {

    private final JdbcTemplate jdbcTemplate;

    public int countStations() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM station", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * station 테이블 전체 조회 (nodename -> nodeid 매핑 반환)
     */
    public Map<String, String> findAllStationNameToIdMap() {
        Map<String, String> nameToId = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT nodeid, nodename FROM station", rs -> {
            String nodeid = rs.getString("nodeid");
            String nodename = rs.getString("nodename");
            if (nodeid != null && nodename != null) {
                nameToId.put(nodename.trim(), nodeid.trim());
            }
        });
        return nameToId;
    }

    /**
     * station 테이블 일괄 Upsert (batchUpdate)
     */
    public int batchUpsertStations(List<TagoStationDTO> stationList) {
        if (stationList == null || stationList.isEmpty()) {
            return 0;
        }
        String sql = """
                INSERT INTO station (nodeid, nodename, citycode, cityname, node_address)
                SELECT ?, ?, ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM station s WHERE s.nodename = ? AND s.nodeid <> ?
                )
                ON CONFLICT (nodeid) DO UPDATE SET
                    nodename = EXCLUDED.nodename,
                    citycode = EXCLUDED.citycode,
                    cityname = EXCLUDED.cityname,
                    node_address = EXCLUDED.node_address
                """;

        jdbcTemplate.batchUpdate(sql, stationList, stationList.size(), (ps, st) -> {
            ps.setString(1, st.getNodeid());
            ps.setString(2, st.getNodename());
            ps.setString(3, st.getCitycode());
            ps.setString(4, st.getCityname());
            ps.setString(5, st.getNodeAddress());
            ps.setString(6, st.getNodename());
            ps.setString(7, st.getNodeid());
        });
        return stationList.size();
    }

    /**
     * KTX 열차 마스터(train) 일괄 Upsert (vehiclekndnm = 'KTX' 고정)
     */
    public int batchUpsertKtxTrains(Map<Integer, String> trainLineMap) {
        if (trainLineMap == null || trainLineMap.isEmpty()) {
            return 0;
        }
        List<Map.Entry<Integer, String>> entries = new ArrayList<>(trainLineMap.entrySet());
        String sql = """
                INSERT INTO train (trainno, vehiclekndnm, line_category)
                VALUES (?, 'KTX', ?)
                ON CONFLICT (trainno) DO UPDATE SET
                    line_category = EXCLUDED.line_category
                """;

        jdbcTemplate.batchUpdate(sql, entries, entries.size(), (ps, entry) -> {
            ps.setInt(1, entry.getKey());
            ps.setString(2, entry.getValue());
        });
        return entries.size();
    }
}
