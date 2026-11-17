package com.kedu.tago.station;

import com.kedu.tago.station.dto.StationResponseDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface StationMapper {

    List<StationResponseDTO> selectStationList();
}
