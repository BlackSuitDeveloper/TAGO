package com.kedu.tago.station;

import com.kedu.tago.common.ApiPaths;
import com.kedu.tago.common.response.ResWrapper;
import com.kedu.tago.station.dto.StationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 역 조회 API */
@RestController
@RequestMapping(ApiPaths.V1 + "/station")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    /** 역 목록 조회 (GET /api/v1/station) */
    @GetMapping
    public ResWrapper<List<StationResponseDTO>> getStationList() {
        return ResWrapper.list(stationService.getStationList());
    }
}
