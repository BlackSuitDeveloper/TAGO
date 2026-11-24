package com.kedu.tago.station;

import com.kedu.tago.station.dto.StationResponseDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/** 역 매퍼 */
@Mapper
public interface StationMapper {
    /** 역 목록 조회 */
    List<StationResponseDTO> selectStationList();
}
