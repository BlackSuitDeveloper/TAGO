package com.kedu.tago.station;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StationService {

    private final StationDAO stationDAO;

    public List<StationDTO> getStationList() {
        return stationDAO.selectStationList();
    }
}
