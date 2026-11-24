package com.kedu.tago.station;

import com.kedu.tago.station.dto.StationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 역 조회 서비스 */
@Service
@RequiredArgsConstructor
public class StationService {

    private final StationMapper stationMapper;

    /** 역 목록 조회 */
    public List<StationResponseDTO> getStationList() {
        return stationMapper.selectStationList();
    }
}
