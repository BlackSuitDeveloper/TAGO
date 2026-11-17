package com.kedu.tago.station;

import com.kedu.tago.station.dto.StationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/station")
@RequiredArgsConstructor
public class StationController {

    private final StationService stationService;

    /**
     * 역 목록 조회 (GET /api/station)
     */
    @GetMapping
    public ResponseEntity<List<StationResponseDTO>> getStationList() {
        return ResponseEntity.ok(stationService.getStationList());
    }
}
