package com.kedu.tago.seat;

import com.kedu.tago.seat.dto.SeatResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seat")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @GetMapping
    public ResponseEntity<SeatResponseDTO> getSeats(@RequestParam Long scheduleSeq, @RequestParam(required = false) Integer carNo) {
        // 좌석 목록 조회 GET /api/seat?scheduleSeq={scheduleSeq}&carNo={carNo}
        // @param scheduleSeq 운행 일정 시퀀스 (필수) @param carNo (호차 번호 (선택: null일 경우 1호차 기본 조회 또는 전체 요약))
        // @return 200 OK, 호차별 요약 및 좌석 목록
        SeatResponseDTO response = seatService.getSeats(scheduleSeq, carNo);
        return ResponseEntity.ok(response);
    }
}
