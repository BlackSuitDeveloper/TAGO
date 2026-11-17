package com.kedu.tago.station;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StationDAO {

    private final SqlSession mybatis;

    public List<StationDTO> selectStationList() {
        return mybatis.selectList("Station.selectStationList");
    }
}
