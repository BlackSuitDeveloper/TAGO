package com.kedu.tago.station;

import com.kedu.tago.station.dto.StationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationMapper stationMapper;

    public List<StationResponseDTO> getStationList() {
        return stationMapper.selectStationList();
    }
}
