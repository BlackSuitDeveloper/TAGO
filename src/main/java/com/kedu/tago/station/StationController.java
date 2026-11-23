package com.kedu.tago.station;

import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.station.dto.StationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/station")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    @GetMapping
    public ResWrapper<List<StationResponseDTO>> getStationList() {
        return ResWrapper.list(stationService.getStationList());
    }
}
